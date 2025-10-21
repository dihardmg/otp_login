package com.springboot.otplogin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:noreply@yourdomain.com}")
    private String fromEmail;

    @Value("${app.email.otp.template-enabled:true}")
    private boolean enableHtmlTemplate;

    @Async
    public void sendOtpEmail(String to, String otp) {
        try {
            if (enableHtmlTemplate) {
                sendHtmlOtpEmail(to, otp);
            } else {
                sendSimpleOtpEmail(to, otp);
            }
            log.info("OTP email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    private void sendSimpleOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Your OTP Code - Login Verification");

        String emailBody = buildSimpleEmailBody(otp);
        message.setText(emailBody);

        mailSender.send(message);
    }

    private void sendHtmlOtpEmail(String to, String otp) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("Your OTP Code - Login Verification");

        String htmlBody = buildHtmlEmailBody(otp);
        helper.setText(htmlBody, true);

        mailSender.send(mimeMessage);
    }

    private String buildSimpleEmailBody(String otp) {
        return String.format("""
            Hello,

            Your One-Time Password (OTP) for login verification is:

            %s

            This OTP will expire in 5 minutes.

            If you didn't request this OTP, please ignore this email.

            Best regards,
            OTP Login System
            """, otp);
    }

    private String buildHtmlEmailBody(String otp) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>OTP Verification</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background-color: #f4f4f4;
                        margin: 0;
                        padding: 20px;
                    }
                    .container {
                        max-width: 600px;
                        margin: 0 auto;
                        background-color: white;
                        padding: 30px;
                        border-radius: 10px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    .header {
                        text-align: center;
                        color: #333;
                        margin-bottom: 30px;
                    }
                    .otp-box {
                        background-color: #007bff;
                        color: white;
                        font-size: 32px;
                        font-weight: bold;
                        padding: 20px;
                        text-align: center;
                        border-radius: 8px;
                        margin: 20px 0;
                        letter-spacing: 8px;
                    }
                    .info {
                        background-color: #f8f9fa;
                        padding: 15px;
                        border-left: 4px solid #007bff;
                        margin: 20px 0;
                    }
                    .footer {
                        text-align: center;
                        color: #666;
                        font-size: 14px;
                        margin-top: 30px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>🔐 OTP Verification</h2>
                        <p>Your One-Time Password for login verification</p>
                    </div>

                    <p>Hello,</p>

                    <p>Use the OTP below to complete your login verification:</p>

                    <div class="otp-box">%s</div>

                    <div class="info">
                        <strong>⏰ Important:</strong> This OTP will expire in <strong>5 minutes</strong>.
                    </div>

                    <p>If you didn't request this OTP, please ignore this email. Your account remains secure.</p>

                    <div class="footer">
                        <p>Best regards,<br>OTP Login System</p>
                        <p><em>This is an automated message. Please do not reply to this email.</em></p>
                    </div>
                </div>
            </body>
            </html>
            """, otp);
    }

    @Async
    public void sendWelcomeEmail(String to, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("Welcome to OTP Login System");

            String body = String.format("""
                Hello %s,

                Welcome to OTP Login System! Your account has been successfully created.

                You can now login using our passwordless authentication system with OTP verification.

                Best regards,
                OTP Login System
                """, name != null ? name : "User");

            message.setText(body);
            mailSender.send(message);

            log.info("Welcome email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", to, e.getMessage());
        }
    }

    // Optimized async method with minimal logging for high performance
    @Async
    public void sendWelcomeEmailAsync(String to, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("Welcome to OTP Login System");

            String body = String.format("Hello %s,\n\nWelcome to OTP Login System! Your account has been successfully created.\n\nYou can now login using our passwordless authentication system with OTP verification.\n\nBest regards,\nOTP Login System",
                name != null ? name : "User");

            message.setText(body);
            mailSender.send(message);

            // Minimal logging - only log errors
        } catch (Exception e) {
            // Silent fail for production performance - debug only
            log.debug("Email queuing failed for {}: {}", to, e.getMessage());
        }
    }
}