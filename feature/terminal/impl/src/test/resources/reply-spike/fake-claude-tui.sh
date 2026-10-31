#!/bin/sh
# Fake `claude attach <id>` TUI for AM-507 tests; never the real Claude Code.
#
# Environment:
#   FAKE_TUI_FRAME           file drawn once after the delay (none: an empty screen)
#   FAKE_TUI_DELAY           seconds before the frame (default 0)
#   FAKE_TUI_LOG             log: "early <hex>" for bytes typed before the frame, "frame", then one hex byte per line
#   FAKE_TUI_STUCK=1         the prompt never goes away
#   FAKE_TUI_PID_FILE        receives this client's pid
#   FAKE_TUI_FILLED_FRAME    file drawn when a bracketed paste ends (the reply sits in the input box)
#   FAKE_TUI_SENT_FRAME      file drawn after Enter instead of the default spinner screen (empty box, spinner);
#                            with FAKE_TUI_STUCK=1 neither is drawn after Enter: the text stays in the box
#   FAKE_TUI_IGNORE_HUP=1    SIGHUP is ignored: the client does not end by itself
#   FAKE_TUI_DETACH_EXITS=1  the detach key (ESC [ D) ends the client
#   FAKE_TUI_CHILD_PID_FILE  starts a SIGHUP-immune child in its own session, like a daemon (outside the client's
#                            process group, so killing the group spares it), and receives its pid
# The prompt goes away (screen cleared, spinner drawn) after Enter, a digit or a lone Esc outside a bracketed paste.
if [ "$1" != "attach" ]; then
    echo "fake-claude-tui: unsupported: $*" >&2
    exit 2
fi

if [ -n "$FAKE_TUI_PID_FILE" ]; then echo "$$" > "$FAKE_TUI_PID_FILE"; fi
if [ -n "$FAKE_TUI_CHILD_PID_FILE" ]; then
    # perl (on macOS too, unlike setsid) leaves the client's session and group, then execs: $! is the pid of sleep.
    perl -e 'use POSIX qw(setsid); $SIG{HUP} = "IGNORE"; setsid(); exec @ARGV' sleep 60 > /dev/null 2>&1 &
    echo "$!" > "$FAKE_TUI_CHILD_PID_FILE"
fi

log() { if [ -n "$FAKE_TUI_LOG" ]; then printf '%s\n' "$1" >> "$FAKE_TUI_LOG"; fi; }

# Sets B to the hex of the next input byte, empty at end of input (or on timeout when stty has min 0).
read_byte() { B=$(dd bs=1 count=1 2> /dev/null | od -An -v -tx1 | tr -d ' \n'); }

if [ "$FAKE_TUI_IGNORE_HUP" = "1" ]; then trap '' HUP; fi

answered() {
    if [ "$FAKE_TUI_STUCK" = "1" ]; then return; fi
    if [ -n "$FAKE_TUI_SENT_FRAME" ]; then
        cat "$FAKE_TUI_SENT_FRAME"
    else
        printf '\033[2J\033[H\033[33m✻ Working…\033[0m\r\n'
    fi
}

# After ESC: a lone Esc (nothing within 300 ms) answers; "[" starts a CSI sequence that ends with a byte 0x40-0x7e.
read_escape() {
    stty min 0 time 3
    read_byte
    stty min 1 time 0
    if [ -z "$B" ]; then
        if [ "$in_paste" = 0 ]; then answered; fi
        return
    fi
    log "$B"
    if [ "$B" != "5b" ]; then return; fi
    seq=""
    while :; do
        read_byte
        if [ -z "$B" ]; then return; fi
        log "$B"
        seq="$seq$B"
        v=$((0x$B))
        if [ "$v" -ge 64 ] && [ "$v" -le 126 ]; then break; fi
    done
    case "$seq" in
        3230307e) in_paste=1 ;;
        3230317e)
            in_paste=0
            if [ -n "$FAKE_TUI_FILLED_FRAME" ]; then cat "$FAKE_TUI_FILLED_FRAME"; fi
            ;;
        44) if [ "$FAKE_TUI_DETACH_EXITS" = "1" ]; then exit 0; fi ;;
    esac
}

stty raw -echo opost
sleep "${FAKE_TUI_DELAY:-0}"

# Anything typed before the frame was drawn is a driver that did not wait for the prompt.
stty min 0 time 0
while :; do
    read_byte
    if [ -z "$B" ]; then break; fi
    log "early $B"
done
stty min 1 time 0

if [ -n "$FAKE_TUI_FRAME" ]; then cat "$FAKE_TUI_FRAME"; fi
log "frame"

in_paste=0
while :; do
    read_byte
    if [ -z "$B" ]; then exit 0; fi
    log "$B"
    case "$B" in
        1b) read_escape ;;
        0d | 3[1-9]) if [ "$in_paste" = 0 ]; then answered; fi ;;
    esac
done
