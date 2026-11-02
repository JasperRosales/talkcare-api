import { Router } from 'express';
import { query } from '../db.js';
import { Permission as P } from '../domain/constants.js';
import { authenticate, requirePermission as can } from '../middleware/auth.js';
import * as activity from '../repositories/activityRepository.js';
import * as lookups from '../repositories/lookupRepository.js';
import * as admin from '../services/adminService.js';
import * as auth from '../services/authService.js';
import * as chat from '../services/chatService.js';
import * as profile from '../services/profileService.js';
import * as reports from '../services/reportService.js';
import * as verification from '../services/verificationService.js';

/**
 * Route table. Express 5 forwards rejected promises to the error handler, so handlers
 * can be plain async functions.
 */
export function apiRouter() {
  const r = Router();

  // --- Public -------------------------------------------------------------
  r.get('/health', async (_req, res) => {
    try {
      await query('SELECT 1');
      res.json({ ok: true, database: 'connected', message: 'Connected to the TalkCare database.' });
    } catch (err) {
      res.status(503).json({ ok: false, database: 'unreachable', message: 'The TalkCare database is unreachable.' });
      console.error('Health check failed:', err.message);
    }
  });
  r.get('/lookups', async (_req, res) => res.json(await lookups.listAll()));

  r.post('/auth/login', async (req, res) => res.json(await auth.login(req.body ?? {})));
  r.post('/auth/register', async (req, res) => res.status(201).json(await auth.register(req.body ?? {})));
  r.post('/auth/forgot-password', async (req, res) => res.json(await auth.requestPasswordReset(req.body ?? {})));
  r.post('/auth/reset-password', async (req, res) => res.json(await auth.resetPassword(req.body ?? {})));
  // Tokens are stateless JWTs; logout is acknowledged and the client discards its token.
  r.post('/auth/logout', (_req, res) => res.json({ success: true }));

  // --- Authenticated -------------------------------------------------------
  r.use(authenticate);

  r.get('/auth/me', (req, res) => res.json(req.actor.toJSON()));
  r.patch('/profile', async (req, res) => res.json((await profile.updateProfile(req.actor, req.body ?? {})).toJSON()));
  r.post('/profile/photo', async (req, res) => res.json(await profile.uploadPhoto(req.actor, req.body ?? {})));
  r.get('/notifications', async (req, res) => res.json(await activity.notificationsFor(req.actor.id)));
  r.post('/notifications/read', async (req, res) => {
    await activity.markNotificationsRead(req.actor.id);
    res.json({ success: true });
  });

  r.post('/verifications', can(P.VERIFICATIONS_SUBMIT), async (req, res) =>
    res.json(await verification.submit(req.actor, req.body ?? {})));

  r.post('/reports', can(P.REPORTS_CREATE), async (req, res) =>
    res.status(201).json(await reports.submit(req.actor, req.body ?? {})));
  r.get('/reports', can(P.REPORTS_READ_OWN, P.REPORTS_READ_ASSIGNED, P.REPORTS_READ_ALL), async (req, res) =>
    res.json(await reports.list(req.actor, req.query.status ?? req.query.filter)));
  r.get('/reports/:id', async (req, res) => res.json(await reports.get(req.actor, req.params.id)));
  r.patch('/reports/:id/status', can(P.REPORTS_UPDATE_STATUS), async (req, res) =>
    res.json(await reports.updateStatus(req.actor, req.params.id, req.body ?? {})));

  r.post('/conversations', can(P.CONVERSATIONS_CREATE), async (req, res) =>
    res.json(await chat.openConversation(req.actor, req.body?.serviceType)));
  r.get('/conversations/assigned', can(P.CONVERSATIONS_READ_ASSIGNED), async (req, res) =>
    res.json(await chat.listAssigned(req.actor)));
  r.get('/conversations/:id', async (req, res) => res.json(await chat.getConversation(req.actor, req.params.id)));
  r.get('/conversations/:id/messages', async (req, res) =>
    res.json(await chat.listMessages(req.actor, req.params.id)));
  r.post('/conversations/:id/messages', can(P.MESSAGES_SEND), async (req, res) =>
    res.status(201).json(await chat.sendClientMessage(req.actor, req.params.id, req.body ?? {})));
  r.post('/conversations/:id/replies', can(P.MESSAGES_REPLY), async (req, res) =>
    res.status(201).json(await chat.sendProfessionalReply(req.actor, req.params.id, req.body ?? {})));

  r.get('/admin/metrics', can(P.ADMIN_DASHBOARD), async (_req, res) => res.json(await admin.metrics()));
  r.get('/admin/users', can(P.USERS_READ), async (_req, res) => res.json(await admin.users()));
  r.get('/admin/pending-requests', can(P.VERIFICATIONS_REVIEW, P.REPORTS_UPDATE_STATUS), async (req, res) =>
    res.json(await admin.pendingRequests(req.actor)));
  r.post('/admin/pending-requests/action', can(P.VERIFICATIONS_REVIEW, P.REPORTS_UPDATE_STATUS), async (req, res) =>
    res.json(await admin.processRequest(req.actor, req.body ?? {})));
  r.get('/admin/activity', can(P.ADMIN_ACTIVITY_READ), async (_req, res) => res.json(await admin.recentActivity()));
  r.get('/admin/schedule', can(P.ADMIN_DASHBOARD), async (req, res) => res.json(await admin.schedule(req.query)));

  return r;
}
