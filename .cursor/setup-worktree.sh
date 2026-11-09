#!/bin/sh
# Runs in the new Cursor worktree before the agent starts (.cursor/worktrees.json).
# Installs dependencies so a Cursor writer does not spend its own turns on npm install.
set -e
cd c2s-web && npm ci --prefer-offline --no-audit --no-fund
