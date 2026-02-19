
// services/notificationService.ts
import { Program, WorkoutLog, Settings, Session } from '../types';
import { Capacitor } from '@capacitor/core';

// Simple string hash to generate a numeric ID from a string ID
function simpleHash(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0; // Convert to 32bit integer
  }
  return Math.abs(hash);
}

// Helper to find the next occurrence of a given day of the week
const getNextDateForDay = (dayOfWeek: number, time: string, startWeekOn: number): Date => {
    const [hours, minutes] = time.split(':').map(Number);
    const now = new Date();
    const resultDate = new Date();
    resultDate.setHours(hours, minutes, 0, 0);

    let currentDay = now.getDay(); // 0 = Sunday
    
    // Adjust currentDay if week starts on Monday (1), to make Monday=0...Sunday=6
    if (startWeekOn === 1) {
        currentDay = (currentDay === 0) ? 6 : currentDay - 1;
    }

    let targetDay = dayOfWeek;
    // Adjust targetDay if week starts on Monday (1)
    if (startWeekOn === 1) {
        targetDay = (targetDay === 0) ? 6 : targetDay - 1;
    }
    
    let daysUntilTarget = targetDay - currentDay;

    // If the day is today but the time has passed, or if the day is in the past this week
    if (daysUntilTarget < 0 || (daysUntilTarget === 0 && now.getTime() > resultDate.getTime())) {
        daysUntilTarget += 7; // Schedule for next week
    }

    resultDate.setDate(now.getDate() + daysUntilTarget);
    return resultDate;
};

// Request permissions to send notifications
export const requestPermissions = async (): Promise<boolean> => {
    if (!Capacitor.isNativePlatform()) return false;
    try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        const result = await LocalNotifications.requestPermissions();
        return result.display === 'granted';
    } catch (e) {
        console.error('Error requesting notification permissions', e);
        return false;
    }
};

// Cancel any pending notifications
export const cancelPendingNotifications = async () => {
    if (!Capacitor.isNativePlatform()) return;
    try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        const pending = await LocalNotifications.getPending();
        if (pending.notifications.length > 0) {
            await LocalNotifications.cancel(pending);
             console.log('Cancelled pending notifications.');
        }
    } catch (e) {
        console.error('Error canceling notifications', e);
    }
};

export const setupNotificationChannels = async () => {
    if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== 'android') return;
    try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        await LocalNotifications.createChannel({
            id: 'yp_timers',
            name: 'Temporizadores',
            description: 'Notificaciones para temporizadores de descanso',
            importance: 5, // High importance
            visibility: 1, // Public visibility
            sound: 'default',
            vibration: true,
        });
        await LocalNotifications.createChannel({
            id: 'yp_reminders',
            name: 'Recordatorios',
            description: 'Recordatorios generales de entrenamiento y sueño',
            importance: 4,
            visibility: 1,
            sound: 'default',
            vibration: true,
        });
        console.log("Notification channels created or updated.");
    } catch (e) {
        console.error('Error creating notification channel', e);
    }
};


// Schedule reminders for the week
export const scheduleWorkoutReminders = async (programs: Program[], settings: Settings) => {
    if (!settings.remindersEnabled || !settings.reminderTime || !Capacitor.isNativePlatform()) {
        return;
    }

    // First, clear any old reminders
    await cancelPendingNotifications();
    
    // Dynamically import LocalNotifications
    const { LocalNotifications } = await import('@capacitor/local-notifications');
    
    // Check if permissions are granted
    const permissions = await LocalNotifications.checkPermissions();
    if (permissions.display !== 'granted') {
        console.log("Notification permissions not granted. Skipping scheduling.");
        return;
    }

    const sessionsToSchedule: { session: Session; program: Program }[] = [];
    
    // Gather all sessions that have a specific day of the week assigned
    programs.forEach(program => {
        (program.macrocycles || []).forEach(macro => {
            (macro.blocks || []).flatMap(b => b.mesocycles).forEach(meso => {
                (meso.weeks || []).forEach(week => {
                    (week.sessions || []).forEach(session => {
                        if (session.dayOfWeek !== undefined && session.dayOfWeek >= 0 && session.dayOfWeek <= 6) {
                            sessionsToSchedule.push({ session, program });
                        }
                    });
                });
            });
        });
    });

    if (sessionsToSchedule.length === 0) {
        console.log("No sessions with assigned days to schedule reminders for.");
        return;
    }

    const notifications = sessionsToSchedule.map(({ session, program }, index) => {
        const scheduleDate = getNextDateForDay(session.dayOfWeek!, settings.reminderTime!, settings.startWeekOn);
        return {
            id: 100 + index, // Unique ID for each notification
            title: '🔥 ¡Hora de entrenar!',
            body: `Tu sesión de "${session.name}" del programa "${program.name}" te espera.`,
            schedule: { at: scheduleDate, repeats: true, every: 'week' as const },
            sound: 'default',
            attachments: undefined,
            actionTypeId: '',
            extra: null,
            channelId: 'yp_reminders',
        };
    });
    
    try {
        await LocalNotifications.schedule({ notifications });
        console.log(`Scheduled ${notifications.length} weekly workout reminders.`);
    } catch (e) {
        console.error('Error scheduling notifications', e);
    }
};


export const triggerRestEndNotification = async () => {
    if (!Capacitor.isNativePlatform()) {
        console.log("Rest notification only on native");
        return;
    }
    try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        const permissions = await LocalNotifications.checkPermissions();
        if (permissions.display !== 'granted') {
           const request = await LocalNotifications.requestPermissions();
           if (request.display !== 'granted') return;
        }

        await LocalNotifications.schedule({
            notifications: [{
                id: Math.floor(Math.random() * 10000) + 1,
                title: 'Descanso Terminado',
                body: '¡A por la siguiente serie!',
                schedule: { at: new Date(Date.now() + 100) },
                sound: 'default',
                channelId: 'yp_timers'
            }]
        });
    } catch (e) {
        console.error('Error triggering rest end notification', e);
    }
};


export const scheduleQuestionnaireNotification = async (logId: string, sessionName: string) => {
    if (!Capacitor.isNativePlatform()) return;
    try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        const permissions = await LocalNotifications.checkPermissions();
        if (permissions.display !== 'granted') {
           const request = await LocalNotifications.requestPermissions();
           if (request.display !== 'granted') return;
        }

        const notificationId = simpleHash(logId);
        await LocalNotifications.schedule({
            notifications: [{
                id: notificationId,
                title: '¿Cómo te sientes hoy?',
                body: `Responde el cuestionario sobre tu sesión de "${sessionName}" de ayer.`,
                schedule: { at: new Date(Date.now() + 24 * 60 * 60 * 1000) },
                // For testing:
                // schedule: { at: new Date(Date.now() + 30 * 1000) }, 
                sound: 'default',
                channelId: 'yp_reminders'
            }]
        });
        console.log(`Scheduled questionnaire notification for log ${logId} (notif id ${notificationId})`);
    } catch (e) {
        console.error('Error scheduling questionnaire notification', e);
    }
};

export const scheduleBedtimeReminder = async (bedTime: string) => {
    if (!Capacitor.isNativePlatform()) return;
    try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        const permissions = await LocalNotifications.checkPermissions();
        if (permissions.display !== 'granted') {
            await LocalNotifications.requestPermissions();
        }

        const [hours, minutes] = bedTime.split(':').map(Number);
        const reminderDate = new Date();
        reminderDate.setHours(hours, minutes, 0, 0);

        // Subtract 30 minutes
        reminderDate.setMinutes(reminderDate.getMinutes() - 30);

        // Handle date roll over if it's already past the time today
        // Note: This logic assumes we want to schedule for the immediate next occurrence
        if (reminderDate.getTime() < Date.now()) {
             reminderDate.setDate(reminderDate.getDate() + 1);
        }

        // Use a fixed ID for the bedtime reminder to easily overwrite it
        const notificationId = 999; 
        
        // Cancel existing bedtime reminder
        await LocalNotifications.cancel({ notifications: [{ id: notificationId }] });

        await LocalNotifications.schedule({
            notifications: [{
                id: notificationId,
                title: '🌙 Hora de desconectar',
                body: `Tu ventana de sueño óptima comienza en 30 minutos (${bedTime}). ¡Prepara tu descanso!`,
                schedule: { at: reminderDate },
                sound: 'default',
                channelId: 'yp_reminders',
                extra: { type: 'bedtime_reminder' } // Used to handle click
            }]
        });
        console.log(`Scheduled bedtime reminder for ${reminderDate.toLocaleTimeString()}`);

    } catch (e) {
        console.error('Error scheduling bedtime reminder', e);
    }
};
