FROM libretranslate/libretranslate
# 修复 ModelScope 运行时 `su libretranslate` 因 nologin 失败：把该用户 shell 改为 /bin/bash
# 用 awk 直接改 /etc/passwd，避免 sed 在 Dockerfile shell 中正则转义出错
RUN awk -F: 'BEGIN{OFS=":"}$1=="libretranslate"{$7="/bin/bash"}{print}' /etc/passwd > /tmp/passwd && mv /tmp/passwd /etc/passwd
# 运行时：先按 LT_LOAD_ONLY 预下载语言包，再以 7860 启动（匹配 ModelScope Docker 模式）
ENTRYPOINT ["sh", "-c", "libretranslate --load-only $LT_LOAD_ONLY && exec gunicorn libretranslate.main:app --bind 0.0.0.0:7860 --workers 1 --timeout 60"]