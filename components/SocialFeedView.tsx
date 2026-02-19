
// components/SocialFeedView.tsx
import React, { useState, useEffect } from 'react';
import { SocialPost } from '../types';
import { socialService } from '../services/socialService';
import Card from './ui/Card';
import { TrophyIcon, FlameIcon, DumbbellIcon, ClockIcon } from './icons';
import Button from './ui/Button';

const PostCard: React.FC<{ post: SocialPost }> = ({ post }) => {
    return (
        <div className="bg-slate-900/50 border border-white/10 rounded-2xl p-4 mb-4">
            <div className="flex items-center gap-3 mb-3">
                <div className="w-10 h-10 rounded-full bg-slate-700 flex items-center justify-center font-bold text-white">
                    {post.authorName.charAt(0)}
                </div>
                <div>
                    <p className="text-sm font-bold text-white">{post.authorName}</p>
                    <p className="text-[10px] text-slate-400">{new Date(post.date).toLocaleDateString()}</p>
                </div>
                {post.type === 'pr_alert' && (
                    <span className="ml-auto bg-yellow-500/20 text-yellow-400 text-[10px] font-black px-2 py-1 rounded uppercase tracking-wider border border-yellow-500/30">
                        Nuevo PR
                    </span>
                )}
            </div>

            {post.content.text && <p className="text-sm text-slate-300 mb-3">"{post.content.text}"</p>}

            {post.content.workoutData && (
                <div className="bg-black/40 rounded-xl p-3 border border-white/5">
                    <h4 className="text-xs font-bold text-white mb-2 flex items-center gap-2">
                        <DumbbellIcon size={14} className="text-primary-color"/>
                        {post.content.workoutData.sessionName}
                    </h4>
                    <div className="grid grid-cols-2 gap-2 text-xs text-slate-400">
                        {post.content.workoutData.highlightExercise && (
                             <div className="col-span-2 text-white font-bold">
                                 {post.content.workoutData.highlightExercise}: {post.content.workoutData.highlightWeight}kg
                             </div>
                        )}
                        <div className="flex items-center gap-1"><ClockIcon size={12}/> {post.content.workoutData.duration} min</div>
                        <div className="flex items-center gap-1"><FlameIcon size={12}/> {post.content.workoutData.volume} kg vol</div>
                    </div>
                </div>
            )}

            <div className="flex items-center gap-4 mt-4 pt-3 border-t border-white/5">
                <button className="flex items-center gap-1 text-xs font-bold text-slate-400 hover:text-red-400 transition-colors">
                    <FlameIcon size={16}/> {post.likes.length} Fuego
                </button>
                 <button className="flex items-center gap-1 text-xs font-bold text-slate-400 hover:text-white transition-colors">
                    💬 {post.comments.length} Comentarios
                </button>
            </div>
        </div>
    );
};

const SocialFeedView: React.FC = () => {
    const [posts, setPosts] = useState<SocialPost[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const load = async () => {
            setIsLoading(true);
            try {
                const data = await socialService.getGlobalFeed();
                setPosts(data);
            } finally {
                setIsLoading(false);
            }
        };
        load();
    }, []);

    return (
        <div className="pt-20 pb-32 px-4 animate-fade-in">
            <header className="mb-6">
                <h1 className="text-3xl font-black uppercase tracking-tighter text-white">Comunidad</h1>
                <p className="text-slate-400 text-xs font-bold uppercase tracking-widest mt-1">Global Feed</p>
            </header>

            <div className="mb-6">
                <Card className="bg-gradient-to-r from-purple-900/40 to-blue-900/40 border-primary-color/30 text-center">
                    <TrophyIcon size={40} className="mx-auto text-yellow-400 mb-2"/>
                    <h3 className="text-xl font-bold text-white">Próximamente: Modo Online</h3>
                    <p className="text-sm text-slate-300 mt-2">
                        Conecta con tus amigos, compite en ránkings y valida tus PRs. 
                        Esta es una vista previa de lo que vendrá con la integración en la nube.
                    </p>
                    <Button className="w-full mt-4 !text-xs uppercase font-black" disabled>
                        Conectar Cuenta (Pronto)
                    </Button>
                </Card>
            </div>

            {isLoading ? (
                <div className="text-center text-slate-500 py-10">Cargando feed...</div>
            ) : (
                <div>
                    {posts.map(post => (
                        <PostCard key={post.id} post={post} />
                    ))}
                </div>
            )}
        </div>
    );
};

export default SocialFeedView;
