import cors from 'cors';
import express from 'express';
import { config } from './config.js';
import { errorHandler, notFound } from './middleware/errorHandler.js';
import { apiRouter } from './routes/index.js';

/** Builds the Express app without listening, so it can also be exported to serverless hosts. */
export function createApp() {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', 1);

  app.use(cors({
    origin: config.corsOrigins.includes('*') ? '*' : config.corsOrigins,
    methods: ['GET', 'POST', 'PATCH', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Session-Token'],
    maxAge: 600,
  }));
  // Images arrive as base64 data URLs (~1.37x the file size).
  app.use(express.json({ limit: '15mb' }));

  app.get('/', (_req, res) => res.json({ name: 'TalkCare API', docs: '/api/health' }));
  app.use('/uploads', express.static(config.uploadDir, {
    index: false,
    dotfiles: 'deny',
    setHeaders: (res) => res.set('X-Content-Type-Options', 'nosniff'),
  }));
  app.use('/api', apiRouter());

  app.use(notFound);
  app.use(errorHandler);
  return app;
}
