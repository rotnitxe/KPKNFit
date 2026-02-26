
// components/TasksView.tsx
import React, { useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { Task } from '../types';
import Button from './ui/Button';
import Card from './ui/Card';
import { PlusIcon, TrashIcon, CheckCircleIcon, SparklesIcon, ChevronRightIcon } from './icons';

const TaskItem: React.FC<{ task: Task }> = ({ task }) => {
    const { toggleTask, deleteTask } = useAppDispatch();

    return (
        <div className={`glass-card-nested p-4 transition-opacity duration-300 ${task.completed ? 'opacity-50' : 'opacity-100'}`}>
            <div className="flex items-start justify-between gap-4">
                <div className="flex items-start gap-3">
                    <button 
                        onClick={() => toggleTask(task.id)}
                        className="mt-1 flex-shrink-0"
                        aria-label={task.completed ? 'Marcar como no completada' : 'Marcar como completada'}
                    >
                        <div className={`w-6 h-6 rounded-full flex items-center justify-center transition-colors ${task.completed ? 'bg-primary-color' : 'border-2 border-slate-600'}`}>
                            {task.completed && <CheckCircleIcon size={20} className="text-white"/>}
                        </div>
                    </button>
                    <div className="flex-grow">
                        <div className="flex items-center gap-2">
                           {task.generatedBy === 'ai' && <SparklesIcon size={16} className="text-sky-400" />}
                           <h3 className={`font-bold text-white ${task.completed ? 'line-through' : ''}`}>{task.title}</h3>
                        </div>
                        {task.description && <p className={`text-sm text-slate-400 mt-1 ${task.completed ? 'line-through' : ''}`}>{task.description}</p>}
                         {task.completed && task.completedDate && <p className="text-xs text-slate-500 mt-1">Completada: {new Date(task.completedDate).toLocaleDateString()}</p>}
                    </div>
                </div>
                <button 
                    onClick={() => deleteTask(task.id)} 
                    className="p-2 text-slate-500 hover:text-red-400 flex-shrink-0"
                    aria-label="Eliminar tarea"
                >
                    <TrashIcon size={18}/>
                </button>
            </div>
        </div>
    );
};

const TasksView: React.FC = () => {
    const { tasks } = useAppState();
    const { addTask } = useAppDispatch();
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!title.trim()) return;
        // FIX: Removed generatedBy property to match Omit<Task, 'id' | 'completed' | 'generatedBy'> type.
        addTask({ title, description });
        setTitle('');
        setDescription('');
    };
    
    const pendingTasks = tasks.filter(t => !t.completed);
    const completedTasks = tasks.filter(t => t.completed);

    return (
        <div className="space-y-6 tab-bar-safe-area">
            <h1 className="text-4xl font-bold uppercase tracking-wider">Mis Tareas</h1>
            
            <Card>
                <form onSubmit={handleSubmit} className="space-y-4">
                    <input 
                        type="text"
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                        placeholder="Título de la nueva tarea..."
                        className="w-full text-lg"
                        required
                    />
                    <textarea 
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder="Descripción opcional..."
                        rows={2}
                        className="w-full text-sm"
                    />
                    <Button type="submit" className="w-full">
                        <PlusIcon /> Crear Tarea Manual
                    </Button>
                </form>
            </Card>

            <div className="space-y-3">
                <h2 className="text-2xl font-bold">Pendientes ({pendingTasks.length})</h2>
                {pendingTasks.length > 0 ? (
                    pendingTasks.map(task => <TaskItem key={task.id} task={task} />)
                ) : (
                    <p className="text-slate-500 text-center py-4">¡No tienes tareas pendientes!</p>
                )}

                {completedTasks.length > 0 && (
                    <details className="pt-6">
                        <summary className="text-xl font-bold text-slate-400 cursor-pointer flex items-center gap-2">
                           <ChevronRightIcon className="details-arrow" /> Completadas ({completedTasks.length})
                        </summary>
                         <div className="space-y-3 mt-4">
                            {completedTasks.map(task => <TaskItem key={task.id} task={task} />)}
                        </div>
                    </details>
                )}
            </div>
        </div>
    );
};

export default TasksView;
