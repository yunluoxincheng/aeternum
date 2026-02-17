#!/bin/bash
# Aeternum Session Start Hook
# Load and display CLAUDE.md project specification at session start

# Determine project directory
if [ -n "$CLAUDE_PROJECT_DIR" ]; then
    PROJECT_DIR="$CLAUDE_PROJECT_DIR"
else
    # Fallback: use script directory to find project root
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
fi

CLAUDE_MD="$PROJECT_DIR/CLAUDE.md"

# Get startup mode
if [ -n "$1" ]; then
    STARTUP_MODE="$1"
else
    STARTUP_MODE=""
fi

# Execute different logic based on startup mode
case "$STARTUP_MODE" in
    startup|"")
        # New session startup
        if [ -f "$CLAUDE_MD" ]; then
            echo "Aeternum Project Specification"
            echo "================================"
            echo ""
            head -50 "$CLAUDE_MD"
            echo "================================"
            echo ""
            echo "Checkpoint loaded. Before any code modification:"
            echo " 1. Call aeternum-checkpoint first"
            echo " 2. Identify task type (crypto/protocol/android/bridge/invariant)"
            echo " 3. Read required documentation"
            echo " 4. Call corresponding domain-specific skill"
        fi
        ;;
    resume|"")
        # Resume session
        if [ -f "$CLAUDE_MD" ]; then
            echo "Resuming Session - Continuing previous work"
            echo "================================"
            echo ""
            head -20 "$CLAUDE_MD"
            echo "================================"
            echo ""
            echo "Project specification loaded."
            echo "Continuing previous work progress..."
        fi
        ;;
    *)
        # Fallback - also show documentation
        if [ -f "$CLAUDE_MD" ]; then
            echo "Aeternum Project Specification"
            echo "================================"
            echo ""
            head -30 "$CLAUDE_MD"
            echo "================================"
            echo ""
            echo "Checkpoint loaded. Before any code modification:"
            echo " 1. Call aeternum-checkpoint first"
            echo " 2. Identify task type (crypto/protocol/android/bridge/invariant)"
            echo " 3. Read required documentation"
            echo " 4. Call corresponding domain-specific skill"
        fi
        ;;
esac
