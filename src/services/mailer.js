import nodemailer from 'nodemailer';
import { config } from '../config.js';

let transporter;

export function isConfigured() {
  return config.smtp.configured;
}

export async function sendMail({ to, subject, text }) {
  transporter ??= nodemailer.createTransport({
    host: config.smtp.host,
    port: config.smtp.port,
    secure: config.smtp.port === 465,
    requireTLS: config.smtp.port !== 465,
    auth: { user: config.smtp.username, pass: config.smtp.password },
    connectionTimeout: 15_000,
    socketTimeout: 15_000,
  });
  await transporter.sendMail({ from: config.smtp.from, to, subject, text });
}
