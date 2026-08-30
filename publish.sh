#!/bin/bash
# iceBrowser 沙箱端发布脚本
# 用法: ./publish.sh "提交信息"
# 沙箱只把源码 tar 流式推到 Termux,
# build/commit/push 在 Termux 端的 build.sh + publish.sh 完成.
# 沙箱端永不再 exec build - 沙箱没 aapt/dx, build 必然走 ssh 慢路径.
set -e

HOST="${ICEBROWSER_SSH_HOST:-2408:8435:3940:874e:b1:a7ff:fe4b:d8c7}"
KEY="${HOME}/.ssh/id_ed25519"
PROJ="$(cd "$(dirname "$0")" && pwd)"
REMOTE="/data/data/com.termux/files/home/icebrowser"

[ -z "$1" ] && { echo "用法: $0 \"提交信息\""; exit 1; }

SSH="ssh -6 -i $KEY -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -p 8022 $HOST"
SSH_TAR="$SSH -- tar"

echo "→ 同步源码 → Termux (流式 tar)"
tar czf - --exclude=build --exclude='*.apk' --exclude='*.jks' -C "$PROJ" . | \
    $SSH_TAR -C $REMOTE -xzf -

echo "→ Termux: build + commit + push"
$SSH "cd $REMOTE && ./build.sh 2>&1 | tail -3 && \
    git add -A && \
    if [ -n \"\$(git status --porcelain)\" ]; then \
        git commit -qm '$1' && \
        git push -f origin master 2>&1 | tail -5 && \
        echo '✓ 推送成功'; \
    else \
        echo '无新变更'; \
    fi && git log --oneline -3"

echo "✓ 完成 仓库: https://github.com/ice-wocker/iceBrowser"