
// services/socialService.ts
import { SocialPost, SocialProfile, SocialComment, WorkoutLog } from '../types';

// This is a Placeholder Service for Future Firebase Integration
// Currently it simulates network calls and returns mock data

const MOCK_PROFILES: SocialProfile[] = [
    {
        uid: 'user_001',
        username: 'GymRat99',
        athleteType: 'powerlifter',
        stats: { totalWorkouts: 120, ipfPoints: 450, followerCount: 1200, followingCount: 50 },
        badges: ['volume_10k', 'consistency_100']
    },
    {
        uid: 'user_002',
        username: 'SarahLifts',
        athleteType: 'bodybuilder',
        stats: { totalWorkouts: 85, ipfPoints: 380, followerCount: 3400, followingCount: 200 },
        badges: ['pr_bench_100kg']
    }
];

const MOCK_POSTS: SocialPost[] = [
    {
        id: 'post_001',
        authorId: 'user_001',
        authorName: 'GymRat99',
        date: new Date().toISOString(),
        type: 'pr_alert',
        content: {
            text: '¡Finalmente rompí la barrera de los 200kg en Peso Muerto!',
            workoutData: {
                sessionName: 'Día de Pierna Pesado',
                volume: 12000,
                duration: 90,
                highlightExercise: 'Peso Muerto',
                highlightWeight: 200
            }
        },
        likes: ['user_002', 'user_003'],
        comments: [
            { id: 'c1', authorId: 'user_002', authorName: 'SarahLifts', text: '¡Increíble! 🔥', date: new Date().toISOString() }
        ]
    }
];

export const socialService = {
    
    // --- AUTHENTICATION (Placeholder) ---
    async loginWithGoogle(): Promise<SocialProfile | null> {
        console.log("SocialService: Logging in with Google...");
        // In future: await Firebase.auth().signInWithCredential(...)
        return MOCK_PROFILES[0];
    },

    // --- FEED ---
    async getGlobalFeed(): Promise<SocialPost[]> {
        console.log("SocialService: Fetching feed...");
        // In future: await Firebase.firestore().collection('posts').orderBy('date', 'desc').limit(20).get()
        // Simulate network delay
        await new Promise(resolve => setTimeout(resolve, 800));
        return MOCK_POSTS;
    },

    async getGymFeed(gymName: string): Promise<SocialPost[]> {
         console.log(`SocialService: Fetching feed for ${gymName}...`);
         // Filter mock posts by some gym logic or return generic
         return MOCK_POSTS;
    },

    // --- INTERACTION ---
    async likePost(postId: string): Promise<boolean> {
        console.log(`SocialService: Liking post ${postId}`);
        return true;
    },

    async commentOnPost(postId: string, text: string): Promise<SocialComment> {
        console.log(`SocialService: Commenting on ${postId}: ${text}`);
        return {
            id: crypto.randomUUID(),
            authorId: 'current_user',
            authorName: 'Yo',
            text: text,
            date: new Date().toISOString()
        };
    },

    // --- PUBLISHING ---
    async shareWorkout(log: WorkoutLog): Promise<boolean> {
        console.log("SocialService: Sharing workout to cloud...", log.id);
        // Transform WorkoutLog to SocialPost format and push to Firestore
        return true;
    },

    // --- LEADERBOARDS ---
    async getLeaderboard(type: 'squat' | 'bench' | 'deadlift' | 'volume'): Promise<{username: string, value: number}[]> {
        return [
            { username: 'GymRat99', value: 200 },
            { username: 'SarahLifts', value: 140 },
            { username: 'You', value: 100 }
        ];
    }
};
