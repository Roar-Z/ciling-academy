package com.wordspirit.module.user.service;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * 邮件发送服务
 */
@Slf4j
@Service
public class MailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${spring.mail.host:}")
    private String host;

    /** 品牌名（官网一致） */
    private static final String BRAND = "词灵学园";

    /** 邮件内联 logo（resources/mail/logo.png） */
    private static final String LOGO_CID = "brandLogo";

    public void sendVerifyCode(String to, String code) {
        if (StrUtil.isBlank(host)) {
            log.info("[DEV] 邮箱验证码 to={} code={}", to, code);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, BRAND);
            helper.setTo(to);
            helper.setSubject(BRAND + " - 验证码");
            // logo 作为内联附件嵌入邮件，避免依赖外链
            helper.addInline(LOGO_CID, new ClassPathResource("mail/logo.png"));
            helper.setText(buildHtml(code), true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("发送验证码邮件失败 to={}", to, e);
        }
    }

    private String buildHtml(String code) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n"
                + "<head><meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>\n"
                + "<body style=\"margin:0;padding:0;background:#fafafa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',Helvetica,Arial,sans-serif;\">\n"
                + "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fafafa;padding:48px 0;\">\n"
                + "    <tr><td align=\"center\">\n"
                + "      <table role=\"presentation\" width=\"580\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:580px;max-width:92%;background:#ffffff;border:1px solid #ececec;border-radius:8px;\">\n"
                + "        <!-- 品牌头部 -->\n"
                + "        <tr><td style=\"padding:40px 44px 24px;\">\n"
                + "          <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>\n"
                + "            <td style=\"width:34px;height:34px;border-radius:6px;overflow:hidden;\"><img src=\"cid:" + LOGO_CID + "\" alt=\"" + BRAND + "\" width=\"34\" height=\"34\" style=\"width:34px;height:34px;display:block;\"></td>\n"
                + "            <td style=\"padding-left:12px;font-size:18px;font-weight:600;color:#1a1a1a;letter-spacing:2px;\">" + BRAND + "</td>\n"
                + "          </tr></table>\n"
                + "        </td></tr>\n"
                + "        <!-- 正文 -->\n"
                + "        <tr><td style=\"padding:8px 44px 0;\">\n"
                + "          <div style=\"font-size:22px;font-weight:600;color:#1a1a1a;\">邮箱验证码</div>\n"
                + "          <div style=\"font-size:14px;line-height:24px;color:#6b6b6b;margin-top:10px;\">您好，您正在验证邮箱身份。请将下方验证码填入对应页面，完成操作即可。</div>\n"
                + "        </td></tr>\n"
                + "        <!-- 验证码 -->\n"
                + "        <tr><td style=\"padding:28px 44px 0;\">\n"
                + "          <div style=\"font-size:40px;font-weight:700;letter-spacing:14px;color:#1a1a1a;font-family:'Helvetica Neue',Arial,sans-serif;padding-left:14px;\">" + code + "</div>\n"
                + "        </td></tr>\n"
                + "        <tr><td style=\"padding:14px 44px 0;\">\n"
                + "          <div style=\"font-size:13px;color:#9a9a9a;\">验证码有效期 10 分钟，仅用于本次操作，请勿转发给他人。</div>\n"
                + "        </td></tr>\n"
                + "        <!-- 页脚 -->\n"
                + "        <tr><td style=\"padding:36px 44px 40px;\">\n"
                + "          <div style=\"height:1px;background:#efefef;margin-bottom:18px;\"></div>\n"
                + "          <div style=\"font-size:12px;line-height:20px;color:#aaaaaa;\">© " + java.time.Year.now().getValue() + " " + BRAND + "　保留所有权利</div>\n"
                + "          <div style=\"font-size:12px;line-height:20px;color:#aaaaaa;\">这是一封系统自动发送的邮件，无需回复</div>\n"
                + "        </td></tr>\n"
                + "      </table>\n"
                + "    </td></tr>\n"
                + "  </table>\n"
                + "</body></html>";
    }
}
