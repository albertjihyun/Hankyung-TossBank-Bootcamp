import nodemailer from 'nodemailer';

const transporter = nodemailer.createTransport({
  host: process.env.SMTP_HOST,
  port: Number(process.env.SMTP_PORT),
  secure: false, // STARTTLS
  auth: {
    user: process.env.SMTP_USER,
    pass: process.env.SMTP_PASS,
  },
});

export async function sendMail({ to, subject, text }) {
  if (process.env.DRY_RUN === '1') {
    console.log(`[DRY_RUN] to=${to} | subject=${subject}`);
    return;
  }
  await transporter.sendMail({
    from: `"살래말래" <${process.env.SMTP_USER}>`,
    to,
    subject,
    text,
  });
}
