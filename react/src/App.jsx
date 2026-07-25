if (typeof window !== 'undefined' && typeof window.global === 'undefined') {
    window.global = window;
}

import React, {useState, useEffect, useRef, useCallback} from 'react';
import SockJS from 'sockjs-client';
import {Client} from '@stomp/stompjs';
import {
    MessageSquare,
    Phone,
    User as UserIcon,
    Search,
    Plus,
    Send,
    Mic,
    MicOff,
    Video,
    VideoOff,
    PhoneOff,
    ArrowLeft,
    Check,
    CheckCheck,
    FileText,
    Download,
    Camera,
    LogOut,
    Heart,
    Loader2
} from 'lucide-react';

const DB_NAME = 'WhatsApp_IndexedDB';
const DB_VERSION = 1;
const STORE_MESSAGES = 'messages';

function openDB() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, DB_VERSION);
        request.onupgradeneeded = (e) => {
            const db = e.target.result;
            if (!db.objectStoreNames.contains(STORE_MESSAGES)) {
                const store = db.createObjectStore(STORE_MESSAGES, {keyPath: 'id'});
                store.createIndex('sender', 'sender', {unique: false});
                store.createIndex('receiver', 'receiver', {unique: false});
                store.createIndex('dateTime', 'dateTime', {unique: false});
            }
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function saveMessagesToIDB(msgs) {
    if (!msgs || msgs.length === 0) return;
    try {
        const db = await openDB();
        const tx = db.transaction(STORE_MESSAGES, 'readwrite');
        const store = tx.objectStore(STORE_MESSAGES);
        msgs.forEach(msg => {
            if (msg && msg.id) {
                store.put(msg);
            }
        });
        return new Promise((resolve) => {
            tx.oncomplete = () => resolve();
        });
    } catch (e) {
        console.error("IndexedDB Save Error:", e);
    }
}

async function getMessagesFromIDB(currentUser, targetUser) {
    try {
        const db = await openDB();
        const tx = db.transaction(STORE_MESSAGES, 'readonly');
        const store = tx.objectStore(STORE_MESSAGES);

        return new Promise((resolve) => {
            const request = store.getAll();
            request.onsuccess = () => {
                const allMsgs = request.result || [];
                const filtered = allMsgs.filter(m => (m.sender === currentUser && m.receiver === targetUser) || (m.sender === targetUser && m.receiver === currentUser));
                filtered.sort((a, b) => new Date(a.dateTime || 0) - new Date(b.dateTime || 0));
                resolve(filtered);
            };
            request.onerror = () => resolve([]);
        });
    } catch (e) {
        console.error("IndexedDB Query Error:", e);
        return [];
    }
}

async function clearDB() {
    try {
        const db = await openDB();
        const tx = db.transaction(STORE_MESSAGES, 'readwrite');
        tx.objectStore(STORE_MESSAGES).clear();
        return new Promise((resolve) => {
            tx.oncomplete = () => resolve();
        });
    } catch (e) {
        console.error("IndexedDB Clear Error:", e);
    }
}

async function hashPassword(plainPassword) {
    const encoder = new TextEncoder();
    const data = encoder.encode(plainPassword);
    const hashBuffer = await window.crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

function formatTime(dateTimeStr) {
    if (!dateTimeStr) return '';
    const date = new Date(dateTimeStr);
    return isNaN(date.getTime()) ? '' : date.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
}

function formatDate(dateTimeStr) {
    if (!dateTimeStr) return 'N/A';
    const date = new Date(dateTimeStr);
    return isNaN(date.getTime()) ? 'N/A' : date.toLocaleString(undefined, {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
}

function formatChatDateDivider(dateTimeStr) {
    if (!dateTimeStr) return '';
    const date = new Date(dateTimeStr);
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);

    if (date.toDateString() === today.toDateString()) return "Today";
    if (date.toDateString() === yesterday.toDateString()) return "Yesterday";
    return date.toLocaleDateString(undefined, {weekday: 'long', month: 'short', day: 'numeric', year: 'numeric'});
}

const MadeWithLove = () => (<div style={inlineStyles.madeWithLove}>
    <span>Made with </span><Heart size={13} color="#ea3323" fill="#ea3323"
                                  style={{margin: '0 5px'}}/><span> by </span>
    <a href="https://linkedin.com/in/mridulsaha" target="_blank" rel="noreferrer" style={inlineStyles.link}>
        Mridul Saha
    </a>
</div>);

export default function App() {
    const [authStatus, setAuthStatus] = useState('loading');
    const [currentPath, setCurrentPath] = useState(window.location.pathname);
    const [user, setUser] = useState(null);
    const [wsConnected, setWsConnected] = useState(false);
    const [incomingCall, setIncomingCall] = useState(null);

    const stompClientRef = useRef(null);

    useEffect(() => {
        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission().then(permission => {
                console.log('Notification permission status:', permission);
            });
        }
    }, []);

    useEffect(() => {
        const handlePopState = () => setCurrentPath(window.location.pathname);
        window.addEventListener('popstate', handlePopState);
        return () => window.removeEventListener('popstate', handlePopState);
    }, []);

    const navigate = useCallback((path) => {
        window.history.pushState({}, '', path);
        setCurrentPath(path);
    }, []);

    useEffect(() => {
        const checkAuth = async () => {
            try {
                const res = await fetch('/api/auth/validate', {credentials: 'include'});
                if (res.ok) {
                    const detailRes = await fetch('/api/auth/user/detail', {credentials: 'include'});
                    const userData = detailRes.ok ? await detailRes.json() : await res.json().catch(() => ({}));
                    setUser(userData);
                    setAuthStatus('auth');
                    if (window.location.pathname === '/login' || window.location.pathname === '/') {
                        navigate('/homepage');
                    }
                } else {
                    setAuthStatus('unauth');
                    if (window.location.pathname !== '/register') navigate('/login');
                }
            } catch {
                setAuthStatus('unauth');
                if (window.location.pathname !== '/register') navigate('/login');
            }
        };
        checkAuth().then();
    }, [navigate]);

    const connectWebSocket = useCallback(async () => {
        if (stompClientRef.current) await stompClientRef.current.deactivate();

        const client = new Client({
            webSocketFactory: () => new SockJS('/ws'),
            reconnectDelay: 3000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
            onConnect: () => {
                setWsConnected(true);

                client.subscribe('/user/queue/messages', (msg) => {
                    const parsed = JSON.parse(msg.body);
                    window.dispatchEvent(new CustomEvent('ws-message', {detail: parsed}));
                });

                client.subscribe('/user/queue/signals', (signalMsg) => {
                    const signal = JSON.parse(signalMsg.body);
                    window.dispatchEvent(new CustomEvent('ws-signal', {detail: signal}));

                    if (signal.type === 'VIDEO_CALL_INITIATE' || signal.type === 'AUDIO_CALL_INITIATE') {
                        setIncomingCall({
                            sender: signal.sender || signal.receiver,
                            type: signal.type.startsWith('VIDEO') ? 'video' : 'audio',
                            data: signal.data
                        });
                    }
                });

                client.subscribe('/user/queue/read-receipts', (receiptMsg) => {
                    const receipt = JSON.parse(receiptMsg.body);
                    window.dispatchEvent(new CustomEvent('ws-read-receipt', {detail: receipt}));
                });
            },
            onDisconnect: () => setWsConnected(false),
            onWebSocketClose: () => setWsConnected(false)
        });

        client.activate();
        stompClientRef.current = client;
    }, []);

    useEffect(() => {
        if (authStatus === 'auth') connectWebSocket().then();
        return () => {
            if (stompClientRef.current) stompClientRef.current.deactivate().then();
        };
    }, [authStatus, connectWebSocket]);

    if (authStatus === 'loading') return <div style={inlineStyles.centerScreen}><img src="/icon-green-solid.svg"
                                                                                     alt="WhatsApp" width="50"
                                                                                     height="50"
                                                                                     style={{animation: 'pulse 1.5s infinite'}}/>
    </div>;

    const renderRoute = () => {
        if (authStatus === 'unauth') {
            if (currentPath === '/register') return <RegisterView navigate={navigate}/>;
            return <LoginView navigate={navigate} onLogin={(userData) => {
                setUser(userData);
                setAuthStatus('auth');
                navigate('/homepage');
            }}/>;
        }

        if (currentPath === '/login' || currentPath === '/register' || currentPath === '/') {
            navigate('/homepage');
        }

        return (<WhatsAppLayout
            user={user}
            setUser={setUser}
            navigate={navigate}
            stompClient={stompClientRef.current}
            incomingCall={incomingCall}
            setIncomingCall={setIncomingCall}
            onLogout={async () => {
                try {
                    await fetch('/api/logout', {method: 'POST', credentials: 'include'});
                } catch {
                }
                if (stompClientRef.current) stompClientRef.current.deactivate();
                await clearDB();
                setAuthStatus('unauth');
                setUser(null);
                navigate('/login');
            }}
        />);
    };

    return <div style={inlineStyles.appRoot}>{renderRoute()}</div>;
}

async function sendDesktopNotification(title, body, iconUrl, onClickCallback) {
    if (!('Notification' in window)) {
        console.warn("This browser does not support desktop notifications.");
        return;
    }

    let permission = Notification.permission;

    if (permission === 'default') {
        permission = await Notification.requestPermission();
    }

    if (permission === 'granted') {
        try {
            const notif = new Notification(title, {
                body: body,
                icon: iconUrl || '/icon-green-solid.svg',
                badge: '/icon-green-solid.svg',
                vibrate: [200, 100, 200]
            });

            notif.onclick = () => {
                window.focus(); // Bring the browser tab to the front
                if (onClickCallback) onClickCallback();
                notif.close();
            };
        } catch (err) {
            console.error("Failed to render notification:", err);
        }
    } else {
        console.warn("Notifications are blocked or denied by the user/browser.");
    }
}

function LoginView({navigate, onLogin}) {
    const [credential, setCredential] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const handleLoginSubmit = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        setError('');

        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }

        try {
            const encryptedPassword = await hashPassword(password);
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                credentials: 'include',
                body: JSON.stringify({credential, password: encryptedPassword})
            });

            if (res.ok) {
                const detailRes = await fetch('/api/auth/user/detail', {credentials: 'include'});
                const userData = detailRes.ok ? await detailRes.json() : await res.json().catch(() => ({}));
                onLogin(userData);
            } else {
                const errData = await res.json().catch(() => ({}));
                setError(errData.message || 'Invalid Username/Email or Password.');
            }
        } catch {
            setError('Network connection error.');
        } finally {
            setSubmitting(false);
        }
    };

    return (<div style={inlineStyles.centerScreen}>
        <div style={inlineStyles.authCardContainer}>
            <div style={{display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px'}}>
                <img src="/icon-green-solid.svg" alt="WhatsApp" width="32" height="32"/>
                <h2 style={{color: '#00a884', margin: 0}}>WhatsApp</h2>
            </div>
            {error && <p style={{color: '#ea3323', fontSize: '13px'}}>{error}</p>}
            <form onSubmit={handleLoginSubmit} style={{display: 'flex', flexDirection: 'column', gap: '15px'}}>
                <input type="text" placeholder="Username or Email" value={credential}
                       onChange={e => setCredential(e.target.value)} style={inlineStyles.input} required/>
                <input type="password" placeholder="Password" value={password}
                       onChange={e => setPassword(e.target.value)} style={inlineStyles.input} required/>
                <button type="submit" style={inlineStyles.btnPrimary} disabled={submitting}>
                    {submitting ? 'Authenticating...' : 'Login'}
                </button>
            </form>
            <p style={{marginTop: '15px', fontSize: '13px', color: '#8696a0'}}>
                Don't have an account? <span style={{color: '#00a884', cursor: 'pointer', fontWeight: 'bold'}}
                                             onClick={() => navigate('/register')}>Register</span>
            </p>
        </div>
        <div style={{position: 'relative', marginTop: '20px'}}><MadeWithLove/></div>
    </div>);
}

function RegisterView({navigate}) {
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [profileFile, setProfileFile] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const handleRegister = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        setError('');

        if ('Notification' in window && Notification.permission === 'default') {
            await Notification.requestPermission();
        }

        try {
            const encryptedPassword = await hashPassword(password);
            const userDTO = {firstName, lastName, username, email, password: encryptedPassword};

            const formData = new FormData();
            formData.append('user', new Blob([JSON.stringify(userDTO)], {type: 'application/json'}));
            if (profileFile) formData.append('file', profileFile);

            const res = await fetch('/api/register', {
                method: 'POST', credentials: 'include', body: formData
            });

            if (res.ok) {
                setSuccess('Registration successful! Redirecting to homepage...');
                setTimeout(() => navigate('/homepage'), 1500);
            } else {
                const errData = await res.json().catch(() => ({}));
                setError(errData.message || 'Registration failed.');
            }
        } catch {
            setError('Network error.');
        } finally {
            setSubmitting(false);
        }
    };

    return (<div style={inlineStyles.centerScreen}>
        <div style={inlineStyles.authCardContainer}>
            <div style={{display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px'}}>
                <img src="/icon-green-solid.svg" alt="WhatsApp" width="32" height="32"/>
                <h2 style={{color: '#00a884', margin: 0}}>Register</h2>
            </div>
            {error && <p style={{color: '#ea3323', fontSize: '13px'}}>{error}</p>}
            {success && <p style={{color: '#25d366', fontSize: '13px'}}>{success}</p>}
            <form onSubmit={handleRegister} style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
                <div style={{display: 'flex', gap: '10px'}}>
                    <input type="text" placeholder="First Name" value={firstName}
                           onChange={e => setFirstName(e.target.value)} style={inlineStyles.input} required/>
                    <input type="text" placeholder="Last Name" value={lastName}
                           onChange={e => setLastName(e.target.value)} style={inlineStyles.input} required/>
                </div>
                <input type="text" placeholder="Username" value={username}
                       onChange={e => setUsername(e.target.value)} style={inlineStyles.input} required/>
                <input type="email" placeholder="Email" value={email} onChange={e => setEmail(e.target.value)}
                       style={inlineStyles.input} required/>
                <input type="password" placeholder="Password" value={password}
                       onChange={e => setPassword(e.target.value)} style={inlineStyles.input} required/>

                <label style={{fontSize: '12px', color: '#8696a0'}}>Profile Picture (Optional)</label>
                <input type="file" accept="image/*" onChange={e => setProfileFile(e.target.files[0])}
                       style={{color: '#8696a0', fontSize: '12px'}}/>

                <button type="submit" style={inlineStyles.btnPrimary} disabled={submitting}>
                    {submitting ? 'Registering...' : 'Register'}
                </button>
            </form>
            <p style={{marginTop: '15px', fontSize: '13px', color: '#8696a0'}}>
                Already have an account? <span style={{color: '#00a884', cursor: 'pointer', fontWeight: 'bold'}}
                                               onClick={() => navigate('/login')}>Login</span>
            </p>
        </div>
        <div style={{position: 'relative', marginTop: '20px'}}><MadeWithLove/></div>
    </div>);
}

function WhatsAppLayout({user, stompClient, incomingCall, setIncomingCall, onLogout}) {
    const [activeTab, setActiveTab] = useState('chats');
    const [conversations, setConversations] = useState([]);
    const [activeChatUser, setActiveChatUser] = useState(null);
    const [messages, setMessages] = useState([]);
    const [inputText, setInputText] = useState('');
    const [callSession, setCallSession] = useState(null);
    const [popupImage, setPopupImage] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [callsHistory, setCallsHistory] = useState([]);
    const [otherUserProfile, setOtherUserProfile] = useState(null);

    const [avatarHash, setAvatarHash] = useState(Date.now());
    const [avatarUploading, setAvatarUploading] = useState(false);
    const [avatarMessage, setAvatarMessage] = useState({text: '', isError: false});

    const [isLoadingOlder, setIsLoadingOlder] = useState(false);
    const [hasMoreMsgs, setHasMoreMsgs] = useState(true);
    const [isMobile, setIsMobile] = useState(window.innerWidth <= 768);

    useEffect(() => {
        const handleResize = () => setIsMobile(window.innerWidth <= 768);
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    const activeChatUserRef = useRef(activeChatUser);
    useEffect(() => {
        activeChatUserRef.current = activeChatUser;
    }, [activeChatUser]);

    const messagesEndRef = useRef(null);
    const scrollAreaRef = useRef(null);
    const isScrolledToBottom = useRef(true);
    const textAreaRef = useRef(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({behavior: 'auto'});
    };

    useEffect(() => {
        if (isScrolledToBottom.current) {
            scrollToBottom();
        }
    }, [messages]);

    const fetchCallsHistory = useCallback(async () => {
        try {
            const res = await fetch('/api/calls/history', {credentials: 'include'});
            if (res.ok) {
                const data = await res.json();
                setCallsHistory(data);
            }
        } catch (e) {
            console.error(e);
        }
    }, []);

    useEffect(() => {
        const handleGlobalSignal = (e) => {
            const signal = e.detail;

            if (signal.type === 'VIDEO_CALL_INITIATE' || signal.type === 'AUDIO_CALL_INITIATE') {
                const caller = signal.sender || signal.receiver;
                const callType = signal.type.startsWith('VIDEO') ? 'Video' : 'Audio';

                sendDesktopNotification(`Incoming ${callType} Call! 📞`, `@${caller} is calling you...`, `/api/users/avatar/${caller}?t=${avatarHash}`, () => window.focus());
            }

            if (['END_CALL', 'DECLINE', 'BUSY', 'CANCEL', 'CLEAR_INCOMING_POPUP'].includes(signal.type)) {
                setIncomingCall(null);

                if (signal.type !== 'CLEAR_INCOMING_POPUP') {
                    setTimeout(() => fetchCallsHistory().then(), 400);
                }
            }
        };

        window.addEventListener('ws-signal', handleGlobalSignal);
        return () => window.removeEventListener('ws-signal', handleGlobalSignal);
    }, [setIncomingCall, fetchCallsHistory, avatarHash]);

    const fetchHomepageChats = useCallback(async () => {
        try {
            const res = await fetch('/api/chat/homepage', {credentials: 'include'});
            if (res.ok) {
                const data = await res.json();

                const processedData = data.map(chat => {
                    const isIncoming = chat.sender !== user?.username;
                    const isUnread = chat.isRead === false || chat.read === false;
                    return {
                        ...chat, unreadCount: (isIncoming && isUnread) ? 1 : 0
                    };
                });

                setConversations(processedData.sort((a, b) => new Date(b.dateTime || 0) - new Date(a.dateTime || 0)));
                await saveMessagesToIDB(processedData);
            }
        } catch (e) {
            console.error(e);
        }
    }, [user]);

    useEffect(() => {
        if (user) {
            fetchHomepageChats().then();
        }
    }, [fetchHomepageChats, user]);

    useEffect(() => {
        const handleWsMessage = async (e) => {
            const rawMsg = e.detail;
            if (!rawMsg || !user) return;

            const msg = {
                ...rawMsg,
                text: rawMsg.text || rawMsg.content,
                dateTime: rawMsg.dateTime || rawMsg.timestamp || new Date().toISOString(),
                type: rawMsg.type || 'TEXT'
            };

            const peer = msg.sender === user.username ? msg.receiver : msg.sender;
            const isIncomingFromPeer = msg.sender !== user.username;

            await saveMessagesToIDB([msg]);

            const currentActive = activeChatUserRef.current;
            const isTabHidden = document.hidden;
            const isNotFocusedOnSender = !currentActive || currentActive.username !== msg.sender;

            if (isIncomingFromPeer && (isTabHidden || isNotFocusedOnSender)) {
                const previewText = msg.type === 'TEXT' ? msg.text : `Sent a ${msg.type.toLowerCase()}`;
                sendDesktopNotification(`Message from @${msg.sender}`, previewText, `/api/users/avatar/${msg.sender}?t=${avatarHash}`, () => selectChat({username: msg.sender}) // Clicking opens the chat!
                );
            }

            setConversations(prev => {
                const idx = prev.findIndex(c => (c.sender === peer || c.receiver === peer));
                let updated = [...prev];

                const currentActive = activeChatUserRef.current;
                const isCurrentlyChatting = currentActive?.username === peer;
                const shouldIncrementUnread = isIncomingFromPeer && !isCurrentlyChatting;

                if (idx > -1) {
                    const currentUnread = updated[idx].unreadCount || 0;
                    updated[idx] = {
                        ...msg,
                        unreadCount: isCurrentlyChatting ? 0 : (shouldIncrementUnread ? currentUnread + 1 : currentUnread)
                    };
                } else {
                    updated.push({
                        ...msg, unreadCount: shouldIncrementUnread ? 1 : 0
                    });
                }
                return updated.sort((a, b) => new Date(b.dateTime || 0) - new Date(a.dateTime || 0));
            });

            if (currentActive && (currentActive.username === msg.sender || currentActive.username === msg.receiver)) {
                setMessages(prev => {
                    const tempIndex = prev.findIndex(m => m.id && m.id.toString().startsWith('temp-') && m.text === msg.text);
                    if (tempIndex > -1) {
                        const copy = [...prev];
                        copy[tempIndex] = msg;
                        return copy;
                    }
                    if (prev.some(m => m.id === msg.id)) return prev;
                    return [...prev, msg];
                });

                if (isIncomingFromPeer && currentActive.username === msg.sender) {
                    markChatAsRead(msg.sender).then();
                }
            }
        };

        const handleReadReceipt = (e) => {
            const receipt = e.detail;
            const currentActive = activeChatUserRef.current;
            if (currentActive && currentActive.username === receipt.readBy) {
                setMessages(prev => prev.map(m => m.sender === user?.username ? {...m, isRead: true} : m));
            }
        };

        window.addEventListener('ws-message', handleWsMessage);
        window.addEventListener('ws-read-receipt', handleReadReceipt);
        return () => {
            window.removeEventListener('ws-message', handleWsMessage);
            window.removeEventListener('ws-read-receipt', handleReadReceipt);
        };
    }, [user]);

    const markChatAsRead = async (senderUsername) => {
        try {
            await fetch(`/api/chat/read/${senderUsername}`, {method: 'PUT', credentials: 'include'});
        } catch (e) {
            console.error(e);
        }
    };

    const selectChat = async (targetUser) => {
        setActiveChatUser(targetUser);
        setHasMoreMsgs(true);
        isScrolledToBottom.current = true;
        await markChatAsRead(targetUser.username);

        setConversations(prev => prev.map(c => {
            const peer = c.sender === user?.username ? c.receiver : c.sender;
            return peer === targetUser.username ? {...c, unreadCount: 0} : c;
        }));

        let localMsgs = [];
        if (user?.username) {
            localMsgs = await getMessagesFromIDB(user.username, targetUser.username);
            setMessages(localMsgs);
        }

        try {
            const res = await fetch(`/api/chat/conversation/${targetUser.username}`, {credentials: 'include'});
            if (res.ok) {
                const serverMsgs = await res.json();
                if (serverMsgs && serverMsgs.length > 0) {
                    const serverMsgsAsc = serverMsgs.reverse();
                    await saveMessagesToIDB(serverMsgsAsc);

                    setMessages(prev => {
                        const existingIds = new Set(prev.map(m => m.id));
                        const newOnly = serverMsgsAsc.filter(m => !existingIds.has(m.id));
                        const combined = [...prev, ...newOnly];
                        return combined.sort((a, b) => new Date(a.dateTime || 0) - new Date(b.dateTime || 0));
                    });
                }
            }
        } catch (e) {
            console.error("Error fetching chat updates:", e);
        }
    };

    const loadOlderMessages = async () => {
        if (isLoadingOlder || !hasMoreMsgs || messages.length === 0 || !activeChatUser) return;
        setIsLoadingOlder(true);

        const oldestMsg = messages[0];
        try {
            const res = await fetch(`/api/chat/conversation/${activeChatUser.username}?before=${encodeURIComponent(oldestMsg.dateTime)}`, {credentials: 'include'});
            if (res.ok) {
                const olderMsgs = await res.json();
                if (olderMsgs.length === 0) {
                    setHasMoreMsgs(false);
                } else {
                    const olderMsgsAsc = olderMsgs.reverse();
                    await saveMessagesToIDB(olderMsgsAsc);
                    setMessages(prev => {
                        const existingIds = new Set(prev.map(m => m.id));
                        const newOnly = olderMsgsAsc.filter(m => !existingIds.has(m.id));
                        const combined = [...newOnly, ...prev];
                        return combined.sort((a, b) => new Date(a.dateTime || 0) - new Date(b.dateTime || 0));
                    });
                }
            }
        } catch (e) {
            console.error(e);
        } finally {
            setIsLoadingOlder(false);
        }
    };

    const handleChatScroll = async (e) => {
        const {scrollTop, scrollHeight, clientHeight} = e.target;

        isScrolledToBottom.current = scrollHeight - scrollTop - clientHeight < 100;

        if (scrollTop === 0 && !isLoadingOlder && hasMoreMsgs && messages.length > 0) {
            const oldHeight = scrollHeight;
            await loadOlderMessages();
            setTimeout(() => {
                if (scrollAreaRef.current) {
                    const newHeight = scrollAreaRef.current.scrollHeight;
                    scrollAreaRef.current.scrollTop = newHeight - oldHeight;
                }
            }, 0);
        }
    };

    const sendMessage = (e) => {
        if (e) e.preventDefault();
        if (!inputText.trim() || !activeChatUser || !user) return;
        const textContent = inputText.trim();
        setInputText('');

        const tempId = 'temp-' + Date.now();
        const nowIso = new Date().toISOString();

        const optimisticMsg = {
            id: tempId,
            sender: user.username,
            receiver: activeChatUser.username,
            type: 'TEXT',
            text: textContent,
            content: textContent,
            dateTime: nowIso,
            isRead: false
        };

        setMessages(prev => [...prev, optimisticMsg]);
        isScrolledToBottom.current = true;

        const payload = {receiver: activeChatUser.username, type: 'TEXT', content: textContent};
        stompClient?.publish({destination: '/app/chat.send', body: JSON.stringify(payload)});

        if (textAreaRef.current) {
            textAreaRef.current.focus();
        }
    };

    const handleFileUpload = async (e) => {
        const file = e.target.files[0];
        if (!file || !activeChatUser) return;
        const formData = new FormData();
        formData.append('file', file);
        formData.append('receiver', activeChatUser.username);

        try {
            const res = await fetch('/api/chat/send-file', {method: 'POST', credentials: 'include', body: formData});
            if (res.ok) {
                const responseData = await res.json();
                if (responseData) {
                    await saveMessagesToIDB([responseData]);
                }
                await selectChat(activeChatUser);
            }
        } catch (err) {
            console.error("File upload failed", err);
        }
    };

    const handleAvatarUpdate = async (e) => {
        const file = e.target.files[0];
        if (!file) return;
        setAvatarUploading(true);
        setAvatarMessage({text: '', isError: false});
        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch('/api/users/avatar/update', {
                method: 'PUT', credentials: 'include', body: formData
            });
            if (res.ok) {
                setAvatarHash(Date.now());
                setAvatarMessage({text: 'Avatar updated successfully!', isError: false});
            } else {
                setAvatarMessage({text: 'Failed to update avatar.', isError: true});
            }
        } catch {
            setAvatarMessage({text: 'Network error uploading avatar.', isError: true});
        } finally {
            setAvatarUploading(false);
        }
    };

    const handleUserSearchQuery = async (query) => {
        if (query.length > 30) query = query.slice(0, 30);
        setSearchQuery(query);
        if (query.trim().length < 2) return setSearchResults([]);

        try {
            const res = await fetch(`/api/user/search?query=${encodeURIComponent(query)}`, {credentials: 'include'});
            if (res.ok) {
                const data = await res.json();
                setSearchResults(data.filter(u => u.username !== user?.username));
            }
        } catch (e) {
            console.error(e);
        }
    };

    const fetchOtherUserDetail = async (username) => {
        try {
            const res = await fetch(`/api/user/detail/${username}`, {credentials: 'include'});
            if (res.ok) {
                const data = await res.json();
                setOtherUserProfile(data);
                setActiveTab('other-profile');
            }
        } catch (e) {
            console.error(e);
        }
    };

    const initiateCall = (peerUsername, type) => {
        const signalPayload = {
            receiver: peerUsername, type: type === 'video' ? 'VIDEO_CALL_INITIATE' : 'AUDIO_CALL_INITIATE', data: null
        };
        stompClient?.publish({destination: '/app/call.signal', body: JSON.stringify(signalPayload)});
        setCallSession({peer: peerUsername, type, isInitiator: true});
        setTimeout(() => fetchCallsHistory().then(), 400);
    };

    const renderMessageBubble = (m) => {
        const msgType = (m.type || 'TEXT').toUpperCase();
        switch (msgType) {
            case 'IMAGE':
            case 'FILE':
                return (<div style={inlineStyles.fileContainer}>
                    <FileText size={26} color="#00a884"/>
                    <div style={{flex: 1, overflow: 'hidden'}}>
                        <div style={{
                            fontWeight: 'bold',
                            fontSize: '13px',
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis'
                        }}>
                            Attachment
                        </div>
                    </div>
                    <a href={m.text} target="_blank" rel="noreferrer" download style={inlineStyles.downloadBtn}>
                        <Download size={14} color="#fff"/>
                    </a>
                </div>);
            case 'AUDIO':
            case 'VIDEO':
            case 'CALL':
                return (<div style={{display: 'flex', alignItems: 'center', gap: '8px', padding: '4px 0'}}>
                    <Phone size={16} color="#00a884"/>
                    <span style={{fontSize: '13px', fontWeight: '500'}}>{m.text || m.content}</span>
                </div>);
            case 'TEXT':
            default:
                return <div style={{wordBreak: 'break-word', whiteSpace: 'pre-wrap'}}>{m.text || m.content}</div>;
        }
    };

    const renderSidebarPreviewText = (chat) => {
        const type = (chat.type || 'TEXT').toUpperCase();
        if (type === 'FILE' || type === 'IMAGE') return '📁 Attachment';
        if (type === 'VIDEO' || type === 'AUDIO' || type === 'CALL') return `📞 ${chat.text || 'Call'}`;
        return chat.text;
    };

    const renderDateDivider = (dateTimeStr) => {
        const label = formatChatDateDivider(dateTimeStr);
        if (label !== lastDateLabelRef.current) {
            lastDateLabelRef.current = label;
            return (<div key={`divider-${dateTimeStr}`}
                         style={{display: 'flex', justifyContent: 'center', margin: '15px 0'}}>
                <div style={inlineStyles.dateDivider}>{label}</div>
            </div>);
        }
        return null;
    };
    const lastDateLabelRef = useRef(null);
    lastDateLabelRef.current = null;

    const isChatOpenOnMobile = isMobile && activeChatUser && activeTab === 'chats';
    const showSidebar = !isMobile || !isChatOpenOnMobile;
    const showChatSection = !isMobile || isChatOpenOnMobile;

    return (<div style={inlineStyles.whatsappContainer}>
        <style>{responsiveCss}</style>

        <div style={{
            ...inlineStyles.leftNav,
            display: (isMobile && (isChatOpenOnMobile || activeTab === 'other-profile')) ? 'none' : 'flex'
        }} className="app-left-nav">
            <div style={inlineStyles.leftNavTopGroup} className="nav-top-group">
                <div style={inlineStyles.brandTopLeft} className="brand-logo">
                    <img src="/icon-green-solid.svg" alt="WhatsApp" width="34" height="34"/>
                </div>
                <div style={inlineStyles.navSeparator} className="nav-separator"></div>
                <button
                    style={{...inlineStyles.navBtn, background: activeTab === 'chats' ? '#2a3942' : 'transparent'}}
                    onClick={() => {
                        setActiveTab('chats');
                        setActiveChatUser(null);
                    }}>
                    <MessageSquare size={22} color={activeTab === 'chats' ? '#00a884' : '#8696a0'}/>
                    <span className="mobile-nav-label"
                          style={{color: activeTab === 'chats' ? '#00a884' : '#8696a0'}}>Chats</span>
                </button>
                <button
                    style={{...inlineStyles.navBtn, background: activeTab === 'search' ? '#2a3942' : 'transparent'}}
                    onClick={() => {
                        setActiveTab('search');
                        setActiveChatUser(null);
                    }}>
                    <Search size={22} color={activeTab === 'search' ? '#00a884' : '#8696a0'}/>
                    <span className="mobile-nav-label"
                          style={{color: activeTab === 'search' ? '#00a884' : '#8696a0'}}>Search</span>
                </button>
                <button
                    style={{...inlineStyles.navBtn, background: activeTab === 'calls' ? '#2a3942' : 'transparent'}}
                    onClick={() => {
                        setActiveTab('calls');
                        setActiveChatUser(null);
                        fetchCallsHistory().then();
                    }}>
                    <Phone size={22} color={activeTab === 'calls' ? '#00a884' : '#8696a0'}/>
                    <span className="mobile-nav-label"
                          style={{color: activeTab === 'calls' ? '#00a884' : '#8696a0'}}>Calls</span>
                </button>
            </div>

            <button style={{
                ...inlineStyles.navBtn,
                background: activeTab === 'profile' ? '#2a3942' : 'transparent',
                marginTop: 'auto',
                marginBottom: '10px'
            }} className="nav-profile-btn" onClick={() => {
                setActiveTab('profile');
                setActiveChatUser(null);
            }}>
                <UserIcon size={22} color={activeTab === 'profile' ? '#00a884' : '#8696a0'}/>
                <span className="mobile-nav-label"
                      style={{color: activeTab === 'profile' ? '#00a884' : '#8696a0'}}>Profile</span>
            </button>
        </div>

        {showSidebar && (<div style={inlineStyles.secondaryColumn} className="app-secondary-column">

            <div style={{
                ...inlineStyles.columnHeader, display: activeTab === 'other-profile' ? 'none' : 'flex'
            }}>
                <h2>
                    {activeTab === 'chats' && 'Chats'}
                    {activeTab === 'search' && 'Search Users'}
                    {activeTab === 'calls' && 'Calls History'}
                    {activeTab === 'profile' && 'My Profile'}
                </h2>
            </div>

            {activeTab === 'chats' && (<div style={{
                display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, overflow: 'hidden'
            }}>
                <div style={inlineStyles.scrollableList}>
                    {conversations.length === 0 ? (
                        <div style={inlineStyles.emptyState}>No chats found. Use Search to start a
                            conversation!</div>) : (conversations.map((chat) => {
                        const peerUsername = chat.sender === user?.username ? chat.receiver : chat.sender;

                        return (<div
                            key={chat.id || peerUsername}
                            style={{
                                ...inlineStyles.chatListItem,
                                backgroundColor: activeChatUser?.username === peerUsername ? '#2a3942' : 'transparent'
                            }}
                            onClick={() => selectChat({username: peerUsername}).then()}
                        >
                            <img src={`/api/users/avatar/${peerUsername}?t=${avatarHash}`} alt=""
                                 style={inlineStyles.avatarSm} onClick={(e) => {
                                e.stopPropagation();
                                setPopupImage(`/api/users/avatar/${peerUsername}?t=${avatarHash}`);
                            }}/>
                            <div style={{flex: 1, overflow: 'hidden'}}>
                                <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                    <span style={{fontWeight: 'bold'}}>{peerUsername}</span>
                                    <span style={{
                                        fontSize: '11px', color: '#8696a0'
                                    }}>{formatTime(chat.dateTime)}</span>
                                </div>
                                <div style={{
                                    fontSize: '12px',
                                    color: '#8696a0',
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis'
                                }}>
                                    {renderSidebarPreviewText(chat)}
                                </div>
                            </div>
                            {chat.unreadCount > 0 && (
                                <div style={inlineStyles.bulgeBadge}>{chat.unreadCount}</div>)}
                        </div>);
                    }))}
                </div>
            </div>)}

            {activeTab === 'search' && (<div style={{
                display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, padding: '0 15px 15px 15px'
            }}>
                <input type="text" placeholder="Type username or name..." maxLength={30} value={searchQuery}
                       onChange={(e) => handleUserSearchQuery(e.target.value).then()}
                       style={inlineStyles.input}/>
                <div style={{...inlineStyles.scrollableList, marginTop: '10px'}}>
                    {searchQuery.trim().length >= 2 && searchResults.length === 0 && (
                        <div style={inlineStyles.emptyState}>No users found matching "{searchQuery}"</div>)}
                    {searchQuery.trim().length < 2 && (
                        <div style={inlineStyles.emptyState}>Type at least 2 characters to search...</div>)}
                    {searchResults.map((u) => (
                        <div key={u.username} style={inlineStyles.chatListItem} onClick={() => {
                            setActiveTab('chats');
                            selectChat(u).then();
                        }}>
                            <img src={`/api/users/avatar/${u.username}?t=${avatarHash}`} alt=""
                                 style={inlineStyles.avatarSm}/>
                            <div>
                                <div style={{fontWeight: 'bold'}}>{u.fullName || u.username}</div>
                                <div style={{fontSize: '11px', color: '#8696a0'}}>@{u.username}</div>
                            </div>
                        </div>))}
                </div>
            </div>)}

            {activeTab === 'calls' && (
                <div style={{display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0}}>
                    <div style={inlineStyles.scrollableList}>
                        {callsHistory.length === 0 ? (<div style={inlineStyles.emptyState}>Your call history is
                            empty.</div>) : (callsHistory.map((call) => {
                            const peer = call.caller === user?.username ? call.receiver : call.caller;
                            const isIncoming = call.receiver === user?.username;
                            const callIconColor = call.status === 'MISSED' ? '#ea3323' : '#00a884';

                            return (<div key={call.id} style={inlineStyles.chatListItem}>
                                <div style={{
                                    background: '#2a3942',
                                    width: '40px',
                                    height: '40px',
                                    borderRadius: '50%',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    flexShrink: 0
                                }}>
                                    {call.callType === 'VIDEO' ?
                                        <Video size={18} color={callIconColor}/> :
                                        <Phone size={18} color={callIconColor}/>}
                                </div>
                                <div style={{flex: 1, marginLeft: '10px'}}>
                                    <div style={{fontWeight: 'bold'}}>{peer}</div>
                                    <div style={{
                                        fontSize: '11px',
                                        color: '#8696a0',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '4px'
                                    }}>
                                        {isIncoming ? <ArrowLeft size={10} color={callIconColor}/> :
                                            <ArrowLeft size={10} style={{transform: 'rotate(180deg)'}}
                                                       color={callIconColor}/>}
                                        {formatDate(call.startTime)}
                                    </div>
                                </div>
                                <div style={{display: 'flex', gap: '8px'}}>
                                    <button style={inlineStyles.iconBtnSm}
                                            onClick={() => initiateCall(peer, 'audio')}><Phone size={16}
                                                                                               color="#00a884"/>
                                    </button>
                                    <button style={inlineStyles.iconBtnSm}
                                            onClick={() => initiateCall(peer, 'video')}><Video size={16}
                                                                                               color="#00a884"/>
                                    </button>
                                </div>
                            </div>);
                        }))}
                    </div>
                </div>)}

            {activeTab === 'profile' && user && (<div style={{
                display: 'flex',
                flexDirection: 'column',
                flex: 1,
                minHeight: 0,
                padding: '20px',
                alignItems: 'center',
                overflowY: 'auto'
            }}>
                <div style={{position: 'relative', marginBottom: '15px'}}>
                    <img src={`/api/users/avatar/${user.username}?t=${avatarHash}`} alt="Profile" style={{
                        width: '130px',
                        height: '130px',
                        borderRadius: '50%',
                        objectFit: 'cover',
                        cursor: 'pointer',
                        border: '3px solid #2a3942'
                    }} onClick={() => setPopupImage(`/api/users/avatar/${user.username}?t=${avatarHash}`)}/>
                    <label style={inlineStyles.avatarOverlayBtn}>
                        <Camera size={18} color="#fff"/>
                        <input type="file" accept="image/*" style={{display: 'none'}}
                               onChange={handleAvatarUpdate} disabled={avatarUploading}/>
                    </label>
                </div>
                {avatarUploading && <div style={{
                    fontSize: '12px', color: '#3498db', marginBottom: '10px'
                }}>Uploading...</div>}
                {avatarMessage.text && <div style={{
                    fontSize: '12px',
                    color: avatarMessage.isError ? '#ea3323' : '#25d366',
                    marginBottom: '10px'
                }}>{avatarMessage.text}</div>}

                <div style={{
                    width: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '15px',
                    marginBottom: '30px'
                }}>
                    <div style={inlineStyles.profileDetailItem}><span
                        style={{color: '#00a884', fontSize: '12px', fontWeight: 'bold'}}>Your Name</span>
                        <div style={{fontSize: '16px'}}>{user.fullName || 'N/A'}</div>
                    </div>
                    <div style={inlineStyles.profileDetailItem}><span
                        style={{color: '#00a884', fontSize: '12px', fontWeight: 'bold'}}>Username</span>
                        <div style={{fontSize: '16px'}}>@{user.username}</div>
                    </div>
                    <div style={inlineStyles.profileDetailItem}><span
                        style={{
                            color: '#00a884', fontSize: '12px', fontWeight: 'bold'
                        }}>Email Address</span>
                        <div style={{fontSize: '16px'}}>{user.email || 'N/A'}</div>
                    </div>
                </div>

                <button style={inlineStyles.logoutBtn} onClick={onLogout}>
                    <LogOut size={18}/>
                    <span>Log Out</span>
                </button>
            </div>)}

            {activeTab === 'other-profile' && otherUserProfile && (<div style={{
                ...inlineStyles.secondaryColumn,
                display: isMobile ? 'flex' : (showSidebar ? 'flex' : 'none'),
            }} className="app-secondary-column">
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    padding: '10px 0px',
                    borderBottom: '1px solid #202c33',
                    flexShrink: 0
                }}>
                    <button style={{...inlineStyles.iconBtnSm, marginLeft: '5px'}} onClick={() => {
                        setActiveTab('chats');
                    }}><ArrowLeft size={20} color="#8696a0"/></button>
                    <h2 style={{margin: 0, fontSize: '18px'}}>User Info</h2>
                </div>

                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    padding: '30px 20px',
                    flex: 1,
                    overflowY: 'auto'
                }}>
                    <img src={`/api/users/avatar/${otherUserProfile.username}?t=${avatarHash}`} alt=""
                         style={{
                             width: '150px',
                             height: '150px',
                             borderRadius: '50%',
                             objectFit: 'cover',
                             marginBottom: '15px',
                             border: '3px solid #2a3942',
                             cursor: 'pointer'
                         }}
                         onClick={() => setPopupImage(`/api/users/avatar/${otherUserProfile.username}?t=${avatarHash}`)}/>
                    <h2 style={{margin: '0 0 5px 0'}}>{otherUserProfile.fullName}</h2>
                    <div style={{
                        color: '#8696a0', fontSize: '14px', marginBottom: '30px'
                    }}>@{otherUserProfile.username}</div>

                    <div style={{width: '100%'}}>
                        <div style={inlineStyles.profileDetailItem}>
                                        <span style={{
                                            color: '#00a884', fontSize: '12px', fontWeight: 'bold'
                                        }}>Last Seen</span>
                            <div style={{fontSize: '16px'}}>{formatDate(otherUserProfile.lastSeen)}</div>
                        </div>
                    </div>
                </div>
            </div>)}

            <div style={{padding: '15px', borderTop: '1px solid #2f3b43', textAlign: 'center', flexShrink: 0}}>
                <MadeWithLove/>
            </div>
        </div>)}


        {showChatSection && (<div style={inlineStyles.chatSection} className="app-chat-section">
            {activeChatUser ? (
                <div style={{display: 'flex', flexDirection: 'column', height: '100%', position: 'relative'}}>

                    <div style={inlineStyles.chatHeader}>
                        {/* Back Arrow button visible in Mobile Viewport */}
                        {isMobile && (<button style={inlineStyles.iconBtn} onClick={() => {
                            setActiveChatUser(null);
                            setActiveTab('chats');
                        }}>
                            <ArrowLeft size={22} color="#8696a0"/>
                        </button>)}
                        <div
                            style={{
                                display: 'flex', alignItems: 'center', gap: '15px', cursor: 'pointer', flex: 1
                            }}
                            onClick={() => {
                                fetchOtherUserDetail(activeChatUser.username).then();
                            }}>
                            <img src={`/api/users/avatar/${activeChatUser.username}?t=${avatarHash}`} alt=""
                                 style={inlineStyles.avatarSm} onClick={(e) => {
                                e.stopPropagation();
                                setPopupImage(`/api/users/avatar/${activeChatUser.username}?t=${avatarHash}`);
                            }}/>
                            <div style={{flex: 1, overflow: 'hidden'}}>
                                <div style={{
                                    fontWeight: 'bold',
                                    fontSize: '16px',
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis'
                                }}>{activeChatUser.fullName || activeChatUser.username}</div>
                                <div style={{fontSize: '12px', color: '#8696a0', marginTop: '2px'}}>Click here
                                    for
                                    contact info
                                </div>
                            </div>
                        </div>
                        <div style={{display: 'flex', gap: '15px', paddingRight: '5px'}}>
                            <button style={inlineStyles.iconBtn}
                                    onClick={() => initiateCall(activeChatUser.username, 'video')}><Video
                                size={22}
                                color="#8696a0"/>
                            </button>
                            <button style={inlineStyles.iconBtn}
                                    onClick={() => initiateCall(activeChatUser.username, 'audio')}><Phone
                                size={20}
                                color="#8696a0"/>
                            </button>
                        </div>
                    </div>

                    <div style={inlineStyles.messageScrollArea} onScroll={handleChatScroll} ref={scrollAreaRef}>
                        {isLoadingOlder && (
                            <div style={{display: 'flex', justifyContent: 'center', margin: '10px 0'}}>
                                <Loader2 size={24} color="#00a884"
                                         style={{animation: 'spin 1s linear infinite'}}/>
                            </div>)}
                        {messages.map((m, idx) => {
                            const isMe = m.sender === user?.username;
                            const divider = renderDateDivider(m.dateTime);

                            return (<React.Fragment key={m.id || idx}>
                                {divider}
                                <div style={{
                                    display: 'flex',
                                    justifyContent: isMe ? 'flex-end' : 'flex-start',
                                    margin: '4px 0'
                                }}>
                                    <div style={{
                                        ...inlineStyles.messageBubble,
                                        backgroundColor: isMe ? '#005c4b' : '#202c33'
                                    }}>
                                        {renderMessageBubble(m)}
                                        <div style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'flex-end',
                                            gap: '4px',
                                            fontSize: '10px',
                                            color: 'rgba(255,255,255,0.6)',
                                            marginTop: '4px'
                                        }}>
                                            <span>{formatTime(m.dateTime)}</span>
                                            {isMe && (m.isRead ? <CheckCheck size={14} color="#53bdeb"/> :
                                                <Check size={14} color="rgba(255,255,255,0.6)"/>)}
                                        </div>
                                    </div>
                                </div>
                            </React.Fragment>);
                        })}
                        <div ref={messagesEndRef}/>
                    </div>

                    <div style={inlineStyles.chatInputArea}>
                        <label
                            style={{cursor: 'pointer', display: 'flex', alignItems: 'center', padding: '10px'}}>
                            <Plus size={24} color="#8696a0"/>
                            <input type="file" style={{display: 'none'}} onChange={handleFileUpload}/>
                        </label>
                        <textarea
                            ref={textAreaRef}
                            rows={1} value={inputText} onChange={(e) => setInputText(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter' && !e.shiftKey) {
                                    sendMessage(e);
                                }
                            }}
                            placeholder="Type a message" style={inlineStyles.textAreaDynamic}
                        />
                        <button
                            style={{...inlineStyles.iconBtn, padding: '10px'}}
                            onMouseDown={(e) => {
                                e.preventDefault();
                                sendMessage(e);
                            }}
                        >
                            <Send size={22} color={inputText.trim() ? '#00a884' : '#8696a0'}/>
                        </button>
                    </div>
                </div>) : (<div style={inlineStyles.emptyScreenContainer}>
                <img src="/icon-green.svg" alt="WhatsApp" width="80" height="80"
                     style={{marginBottom: '20px', opacity: 0.5}}/>
                <h1 style={{color: '#e9edef', margin: '0 0 10px 0', fontWeight: '300'}}>WhatsApp for
                    Web</h1>
                <p style={{
                    color: '#8696a0',
                    fontSize: '14px',
                    maxWidth: '400px',
                    textAlign: 'center',
                    lineHeight: '1.5'
                }}>
                    Send and receive messages without keeping your phone online.<br/>
                    Experience seamless messaging across an unlimited number of devices.
                </p>
                <div style={{position: 'absolute', bottom: '40px'}}>
                    <MadeWithLove/>
                </div>
            </div>)}
        </div>)}

        {popupImage && (<div style={inlineStyles.lightboxBackdrop} onClick={() => setPopupImage(null)}>
            <img src={popupImage} alt="Zoomed" style={inlineStyles.lightboxImg}/>
            <button style={inlineStyles.lightboxClose} onClick={() => setPopupImage(null)}><ArrowLeft size={24}
                                                                                                      color="#fff"/>
            </button>
        </div>)}

        {callSession &&
            <CallModal callSession={callSession} stompClient={stompClient} onClose={() => setCallSession(null)}/>}

        {incomingCall && (<div style={inlineStyles.incomingPopup}>
            <div style={{display: 'flex', alignItems: 'center', gap: '15px'}}>
                <div style={{background: '#00a884', borderRadius: '50%', padding: '10px'}}>
                    {incomingCall.type === 'video' ? <Video size={24} color="#fff"/> :
                        <Phone size={24} color="#fff"/>}
                </div>
                <div>
                    <div style={{fontSize: '16px', fontWeight: 'bold'}}>{incomingCall.sender}</div>
                    <div style={{fontSize: '13px', color: '#8696a0'}}>Incoming {incomingCall.type} call</div>
                </div>
            </div>
            <div style={{display: 'flex', gap: '10px', marginTop: '15px'}}>
                <button style={{...inlineStyles.btnPrimary, background: '#25d366', flex: 1}} onClick={() => {
                    stompClient?.publish({
                        destination: '/app/call.signal',
                        body: JSON.stringify({receiver: incomingCall.sender, type: 'ANSWER', data: null})
                    });
                    stompClient?.publish({
                        destination: '/app/call.signal', body: JSON.stringify({
                            receiver: user.username, type: 'CLEAR_INCOMING_POPUP', data: null
                        })
                    });

                    setCallSession({peer: incomingCall.sender, type: incomingCall.type, isInitiator: false});
                    setIncomingCall(null);
                }}>Accept
                </button>
                <button style={{...inlineStyles.btnPrimary, background: '#ea3323', flex: 1}} onClick={() => {

                    stompClient?.publish({
                        destination: '/app/call.signal',
                        body: JSON.stringify({receiver: incomingCall.sender, type: 'DECLINE', data: null})
                    });
                    stompClient?.publish({
                        destination: '/app/call.signal', body: JSON.stringify({
                            receiver: user.username, type: 'CLEAR_INCOMING_POPUP', data: null
                        })
                    });

                    setIncomingCall(null);
                }}>Decline
                </button>
            </div>
        </div>)}
    </div>);
}

const rtcConfig = {
    iceServers: [{urls: 'stun:stun.l.google.com:19302'}, {urls: 'stun:stun1.l.google.com:19302'}, {urls: 'stun:stun2.l.google.com:19302'}, {urls: 'stun:stun3.l.google.com:19302'}, {urls: 'stun:stun4.l.google.com:19302'},

        {
            urls: 'turn:openrelay.metered.ca:80', username: 'openrelay', credential: 'openrelay'
        }, {
            urls: 'turn:openrelay.metered.ca:443', username: 'openrelay', credential: 'openrelay'
        }, {
            urls: 'turn:openrelay.metered.ca:443?transport=tcp', username: 'openrelay', credential: 'openrelay'
        }], iceCandidatePoolSize: 10
};

function CallModal({callSession, stompClient, onClose}) {
    const [connected, setConnected] = useState(false);
    const [micMuted, setMicMuted] = useState(false);
    const [videoMuted, setVideoMuted] = useState(false);

    const localVideoRef = useRef(null);
    const remoteVideoRef = useRef(null);
    const peerConnectionRef = useRef(null);
    const localStreamRef = useRef(null);
    const mediaPromiseRef = useRef(null);
    const iceCandidatesQueue = useRef([]);
    const hasInitialized = useRef(false);
    const isCancelledRef = useRef(false);

    const callSessionRef = useRef(callSession);
    useEffect(() => {
        callSessionRef.current = callSession;
    }, [callSession]);

    const log = (msg, data = '') => console.log(`[WebRTC] ${msg}`, data);

    const sendSignal = useCallback((type, data) => {
        if (stompClient && stompClient.connected) {
            log(`Sending Signal -> [${type}] to ${callSessionRef.current.peer}`);
            stompClient.publish({
                destination: '/app/call.signal',
                body: JSON.stringify({receiver: callSessionRef.current.peer, type: type, data: data})
            });
        }
    }, [stompClient]);

    const stopMediaAndConnection = useCallback(() => {
        log("Stopping media and closing connection");
        isCancelledRef.current = true;

        if (localStreamRef.current) {
            localStreamRef.current.getTracks().forEach(track => {
                track.stop();
                track.enabled = false;
            });
            localStreamRef.current = null;
        }
        if (localVideoRef.current) localVideoRef.current.srcObject = null;
        if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null;

        if (peerConnectionRef.current) {
            peerConnectionRef.current.onicecandidate = null;
            peerConnectionRef.current.ontrack = null;
            peerConnectionRef.current.oniceconnectionstatechange = null;
            peerConnectionRef.current.close();
            peerConnectionRef.current = null;
        }

        iceCandidatesQueue.current = [];
    }, []);

    const handleEndCall = useCallback(() => {
        log("User / System initiated End Call - Notifying peer");
        sendSignal('END_CALL', null);
        stopMediaAndConnection();
        onClose();
    }, [sendSignal, stopMediaAndConnection, onClose]);

    useEffect(() => {
        return () => {
            log("Modal unmounting, running cleanup.");
            stopMediaAndConnection();
        };
    }, [stopMediaAndConnection]);

    const getMediaStream = useCallback(async () => {
        if (localStreamRef.current) return localStreamRef.current;
        if (mediaPromiseRef.current) return await mediaPromiseRef.current;

        isCancelledRef.current = false;

        log(`Requesting getUserMedia (video: ${callSessionRef.current.type === 'video'})`);
        mediaPromiseRef.current = navigator.mediaDevices.getUserMedia({
            audio: {
                echoCancellation: true, noiseSuppression: true, autoGainControl: true
            }, video: callSessionRef.current.type === 'video'
        }).then(stream => {
            log("getUserMedia SUCCESS");
            if (isCancelledRef.current) {
                log("Call cancelled while acquiring media. Stopping tracks.");
                stream.getTracks().forEach(track => track.stop());
                return null;
            }
            localStreamRef.current = stream;

            if (localVideoRef.current && callSessionRef.current.type === 'video') {
                localVideoRef.current.srcObject = stream;
                localVideoRef.current.play().catch(e => {
                    if (e.name !== 'AbortError') console.warn("[WebRTC] Local video play error:", e);
                });
            }
            return stream;
        }).catch(err => {
            console.error("[WebRTC] getUserMedia Error:", err);
            if (!isCancelledRef.current) alert("Microphone/Camera access denied.");
            handleEndCall();
            return null;
        });

        const stream = await mediaPromiseRef.current;
        mediaPromiseRef.current = null;
        return stream;
    }, [handleEndCall]);

    const processBufferedIceCandidates = useCallback(async (pc) => {
        while (iceCandidatesQueue.current.length > 0) {
            try {
                const candidate = iceCandidatesQueue.current.shift();
                log("Processing buffered ICE candidate");
                await pc.addIceCandidate(candidate);
            } catch (e) {
                console.error("[WebRTC] ICE candidate error:", e);
            }
        }
    }, []);

    const setupPeerConnection = useCallback((stream) => {
        if (peerConnectionRef.current) return peerConnectionRef.current;
        log("Initializing new RTCPeerConnection");

        const pc = new RTCPeerConnection(rtcConfig);
        peerConnectionRef.current = pc;

        if (stream) {
            stream.getTracks().forEach(track => {
                pc.addTrack(track, stream);
            });
        }

        pc.ontrack = (event) => {
            log(`Received remote track: ${event.track.kind}`);
            const remoteVideo = remoteVideoRef.current;

            if (remoteVideo) {
                let currentStream = remoteVideo.srcObject;
                if (!currentStream) {
                    currentStream = new MediaStream();
                }

                if (!currentStream.getTracks().includes(event.track)) {
                    currentStream.addTrack(event.track);
                }

                if (remoteVideo.srcObject !== currentStream) {
                    remoteVideo.srcObject = currentStream;
                }

                setConnected(true);

                remoteVideo.play().catch(e => {
                    if (e.name !== 'AbortError') {
                        console.warn("[WebRTC] Remote play blocked:", e);
                    }
                });
            }
        };

        pc.onicecandidate = (event) => {
            if (event.candidate) {
                sendSignal('ICE_CANDIDATE', event.candidate);
            }
        };

        pc.oniceconnectionstatechange = async () => {
            log(`ICE Connection State Changed: ${pc.iceConnectionState}`);

            if (pc.iceConnectionState === 'disconnected') {
                log('ICE Connection disconnected. Attempting ICE Restart...');
                try {
                    if (peerConnectionRef.current && callSessionRef.current?.isInitiator) {
                        const offer = await peerConnectionRef.current.createOffer({iceRestart: true});
                        await peerConnectionRef.current.setLocalDescription(offer);
                        sendSignal('OFFER', offer);
                    }
                } catch (err) {
                    console.error('[WebRTC] ICE Restart Error:', err);
                }
            } else if (pc.iceConnectionState === 'failed' || pc.iceConnectionState === 'closed') {
                log('ICE Connection Failed/Closed. Terminating call.');
                handleEndCall();
            }
        };

        return pc;
    }, [sendSignal, handleEndCall]);

    const initWebRTC = useCallback(async () => {
        if (hasInitialized.current) return;
        hasInitialized.current = true;

        const stream = await getMediaStream();
        if (!stream) return;
        setupPeerConnection(stream);
    }, [getMediaStream, setupPeerConnection]);

    useEffect(() => {
        const handleSignal = async (e) => {
            const signal = e.detail;
            let pc = peerConnectionRef.current;
            log(`Received Signal <- [${signal.type}]`);

            try {
                if (signal.type === 'OFFER') {
                    let stream = await getMediaStream();
                    if (!pc) pc = setupPeerConnection(stream);

                    if (stream && pc) {
                        stream.getTracks().forEach(track => {
                            if (!pc.getSenders().some(s => s.track === track)) pc.addTrack(track, stream);
                        });
                    }

                    await pc.setRemoteDescription(new RTCSessionDescription(signal.data));
                    await processBufferedIceCandidates(pc);

                    const answer = await pc.createAnswer();
                    await pc.setLocalDescription(answer);
                    sendSignal('ANSWER', answer);

                } else if (signal.type === 'ANSWER') {
                    if (signal.data) {
                        if (pc && pc.signalingState === 'have-local-offer') {
                            await pc.setRemoteDescription(new RTCSessionDescription(signal.data));
                            await processBufferedIceCandidates(pc);
                        }
                    } else if (callSessionRef.current?.isInitiator) {
                        let stream = await getMediaStream();
                        if (!pc) pc = setupPeerConnection(stream);

                        if (stream && pc) {
                            stream.getTracks().forEach(track => {
                                if (!pc.getSenders().some(s => s.track === track)) pc.addTrack(track, stream);
                            });
                        }

                        const offer = await pc.createOffer();
                        await pc.setLocalDescription(offer);
                        sendSignal('OFFER', offer);
                    }
                } else if (signal.type === 'ICE_CANDIDATE' && signal.data) {
                    const candidate = new RTCIceCandidate(signal.data);
                    if (pc && pc.remoteDescription && pc.remoteDescription.type) {
                        await pc.addIceCandidate(candidate);
                    } else {
                        iceCandidatesQueue.current.push(candidate);
                    }
                } else if (['END_CALL', 'DECLINE', 'BUSY', 'CANCEL'].includes(signal.type)) {
                    if (signal.type === 'CANCEL' && signal.data && signal.data.silent) return;
                    stopMediaAndConnection();
                    onClose();
                }
            } catch (err) {
                log(`Signal Handling Error: ${err.message}`, err);
                stopMediaAndConnection();
                onClose();
            }
        };

        window.addEventListener('ws-signal', handleSignal);
        initWebRTC().then();

        return () => {
            window.removeEventListener('ws-signal', handleSignal);
        };
    }, []);

    const toggleMic = () => {
        if (localStreamRef.current) {
            const audioTrack = localStreamRef.current.getAudioTracks()[0];
            if (audioTrack) {
                audioTrack.enabled = !audioTrack.enabled;
                setMicMuted(!audioTrack.enabled);
            }
        }
    };

    const toggleVideo = () => {
        if (localStreamRef.current) {
            const videoTrack = localStreamRef.current.getVideoTracks()[0];
            if (videoTrack) {
                videoTrack.enabled = !videoTrack.enabled;
                setVideoMuted(!videoTrack.enabled);
            }
        }
    };

    const localVideoStyle = connected ? {
        position: 'absolute',
        top: '20px',
        right: '20px',
        width: '90px',
        height: '120px',
        borderRadius: '12px',
        border: '2px solid #202c33',
        objectFit: 'contain',
        transform: 'scaleX(-1)',
        boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
        zIndex: 15
    } : {
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        objectFit: 'contain',
        transform: 'scaleX(-1)',
        zIndex: 0,
        filter: 'brightness(0.5)'
    };

    return (<div style={inlineStyles.callModal}>
        <video ref={remoteVideoRef} autoPlay playsInline style={{
            width: '100%',
            height: '100%',
            objectFit: 'contain',
            position: 'absolute',
            top: 0,
            left: 0,
            opacity: connected && callSession.type === 'video' ? 1 : 0,
            pointerEvents: connected && callSession.type === 'video' ? 'auto' : 'none',
            zIndex: 1
        }}/>

        <video ref={localVideoRef} autoPlay playsInline muted style={{
            ...localVideoStyle, display: callSession.type === 'video' ? 'block' : 'none'
        }}/>

        {connected && callSession.type === 'audio' && (
            <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px', zIndex: 10}}>
                <img src={`/api/users/avatar/${callSession.peer}`} alt=""
                     style={{width: '130px', height: '130px', borderRadius: '50%', border: '4px solid #00a884'}}/>
                <h2 style={{margin: 0, fontWeight: '400', color: '#00a884'}}>Voice Call Active</h2>
            </div>)}

        {!connected && callSession.isInitiator && (<div style={{
            position: 'absolute',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: '20px',
            zIndex: 10
        }}>
            <img src={`/api/users/avatar/${callSession.peer}`} alt="" style={{
                width: '110px', height: '110px', borderRadius: '50%', border: '4px solid #2a3942', zIndex: 10
            }}/>
            <h2 style={{margin: 0, fontWeight: '400', zIndex: 10}}>Calling {callSession.peer}...</h2>
        </div>)}

        <div style={inlineStyles.connectedCallerCorner}>
            <img src={`/api/users/avatar/${callSession.peer}`} alt=""
                 style={{width: '35px', height: '35px', borderRadius: '50%'}}/>
            <span style={{fontWeight: 'bold'}}>{callSession.peer}</span>
        </div>

        <div style={inlineStyles.callControlsBar}>
            <button style={{...inlineStyles.callControlBtn, background: micMuted ? '#fff' : '#2a3942'}}
                    onClick={toggleMic}>
                {micMuted ? <MicOff size={22} color="#111b21"/> : <Mic size={22} color="#fff"/>}
            </button>
            {callSession.type === 'video' && (
                <button style={{...inlineStyles.callControlBtn, background: videoMuted ? '#fff' : '#2a3942'}}
                        onClick={toggleVideo}>
                    {videoMuted ? <VideoOff size={22} color="#111b21"/> : <Video size={22} color="#fff"/>}
                </button>)}
            <button style={{...inlineStyles.callControlBtn, backgroundColor: '#ea3323', transform: 'scale(1.1)'}}
                    onClick={handleEndCall}>
                <PhoneOff size={22} color="#fff"/>
            </button>
        </div>
    </div>);
}

const responsiveCss = `
  @keyframes pulse { 0% { transform: scale(0.95); opacity: 0.7; } 50% { transform: scale(1.05); opacity: 1; } 100% { transform: scale(0.95); opacity: 0.7; } }
  @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
  .mobile-nav-label { display: none; }

  html, body, #root {
    background-color: #0b141a !important;
    margin: 0 !important;
    padding: 0 !important;
    width: 100% !important;
    height: 100% !important;
    overflow: hidden !important;
  }


  @media (max-width: 768px) {
    .callModal video {
      width: 100% !important;
      height: 100% !important;
      object-fit: contain !important; /* Prevents mobile cropping/zooming */
    }
  }

  @media (max-width: 768px) {
    .app-left-nav {
      width: 100% !important;
      left: 0 !important;
      right: 0 !important;
      height: 60px !important;
      flex-direction: row !important;
      justify-content: space-around !important;
      align-items: center !important;
      padding: 0 !important;
      top: auto !important;
      bottom: 0 !important;
      border-right: none !important;
      border-top: 1px solid #2f3b43 !important;
      position: fixed !important;
      z-index: 1000 !important;
      background-color: #202c33 !important;
      box-sizing: border-box !important;
    }
    .nav-top-group {
      display: flex !important;
      flex-direction: row !important;
      flex: 3 !important;
      width: 75% !important;
      height: 100% !important;
      justify-content: space-around !important;
      align-items: center !important;
      gap: 0 !important;
      margin: 0 !important;
      padding: 0 !important;
    }
    .brand-logo, .nav-separator { display: none !important; }
    .nav-profile-btn {
      margin: 0 !important;
      padding: 0 !important;
      flex: 1 !important;
      width: 25% !important;
      height: 100% !important;
      border-radius: 0 !important;
      display: flex !important;
      flex-direction: column !important;
      align-items: center !important;
      justify-content: center !important;
      box-sizing: border-box !important;
    }
    .nav-top-group > button {
      flex: 1 !important;
      width: 33.33% !important;
      height: 100% !important;
      margin: 0 !important;
      padding: 0 !important;
      border-radius: 0 !important;
      display: flex !important;
      flex-direction: column !important;
      align-items: center !important;
      justify-content: center !important;
      box-sizing: border-box !important;
    }
    .mobile-nav-label { display: block; font-size: 10px; color: #8696a0; margin-top: 2px; }

    .app-secondary-column {
      width: 100% !important;
      height: 100% !important;
      padding-bottom: 60px !important;
      border-right: none !important;
      box-sizing: border-box !important;
      position: fixed !important;
      top: 0 !important;
      left: 0 !important;
    }
    .app-chat-section {
      width: 100% !important;
      height: 100% !important;
      position: fixed !important;
      top: 0 !important;
      left: 0 !important;
      bottom: 0 !important;
      right: 0 !important;
      z-index: 2000 !important;
      box-sizing: border-box !important;
      background-color: #0b141a !important;
    }
    .message-bubble {
      max-width: 85% !important;
    }
  }
`;

const inlineStyles = {
    appRoot: {
        width: '100%',
        height: '100%',
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: '#0b141a',
        color: '#e9edef',
        fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
        overflow: 'hidden'
    },
    centerScreen: {
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100%',
        width: '100%',
        gap: '20px',
        backgroundColor: '#111b21',
        overflowY: 'auto',
        padding: '20px',
        boxSizing: 'border-box'
    },
    authCardContainer: {
        width: '100%',
        maxWidth: '400px',
        padding: '30px 25px',
        backgroundColor: '#202c33',
        borderRadius: '12px',
        boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
        boxSizing: 'border-box'
    },
    input: {
        padding: '14px',
        borderRadius: '8px',
        border: '1px solid #2f3b43',
        backgroundColor: '#2a3942',
        color: '#e9edef',
        outline: 'none',
        width: '100%',
        boxSizing: 'border-box',
        fontSize: '16px',
        transition: 'border 0.2s'
    },
    btnPrimary: {
        padding: '14px',
        borderRadius: '8px',
        border: 'none',
        backgroundColor: '#00a884',
        color: '#111b21',
        fontWeight: 'bold',
        cursor: 'pointer',
        width: '100%',
        fontSize: '16px',
        transition: 'background 0.2s',
        boxShadow: '0 4px 10px rgba(0, 168, 132, 0.2)'
    },

    whatsappContainer: {
        display: 'flex',
        width: '100%',
        height: '100%',
        boxSizing: 'border-box',
        overflow: 'hidden',
        position: 'relative'
    },

    leftNav: {
        width: '70px',
        backgroundColor: '#202c33',
        borderRight: '1px solid #2f3b43',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '20px 0',
        zIndex: 10,
        boxSizing: 'border-box',
        flexShrink: 0
    },
    leftNavTopGroup: {
        display: 'flex', flexDirection: 'column', gap: '20px', alignItems: 'center'
    },
    brandTopLeft: {display: 'flex', justifyContent: 'center', alignItems: 'center', borderRadius: '12px'},
    navSeparator: {width: '30px', height: '1px', backgroundColor: '#2f3b43', margin: '5px 0'},
    navBtn: {
        background: 'transparent',
        border: 'none',
        cursor: 'pointer',
        padding: '12px',
        borderRadius: '12px',
        transition: 'all 0.2s ease',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center'
    },

    secondaryColumn: {
        width: '380px',
        backgroundColor: '#111b21',
        borderRight: '1px solid #2f3b43',
        display: 'flex',
        flexDirection: 'column',
        flexWrap: 'nowrap',
        height: '100%',
        boxSizing: 'border-box'
    },
    columnHeader: {
        padding: '20px', display: 'flex', alignItems: 'center', borderBottom: '1px solid #202c33', flexShrink: 0
    },
    scrollableList: {
        flex: 1, overflowY: 'auto', paddingBottom: '20px', boxSizing: 'border-box', minHeight: 0
    },
    chatListItem: {
        display: 'flex',
        alignItems: 'center',
        padding: '12px 20px',
        gap: '15px',
        cursor: 'pointer',
        borderBottom: '1px solid #2f3b43',
        transition: 'background 0.1s ease',
        boxSizing: 'border-box'
    },
    avatarSm: {
        width: '48px',
        height: '48px',
        borderRadius: '50%',
        objectFit: 'cover',
        cursor: 'pointer',
        border: '1px solid #2f3b43',
        flexShrink: 0
    },
    bulgeBadge: {
        backgroundColor: '#00a884',
        color: '#111b21',
        borderRadius: '50%',
        width: '20px',
        height: '20px',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        fontSize: '11px',
        fontWeight: 'bold',
        flexShrink: 0
    },

    chatSection: {
        flex: 1,
        backgroundColor: '#0b141a',
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        position: 'relative',
        boxSizing: 'border-box'
    },
    chatHeader: {
        height: '65px',
        backgroundColor: '#202c33',
        display: 'flex',
        alignItems: 'center',
        padding: '0 15px',
        gap: '12px',
        borderBottom: '1px solid #2f3b43',
        flexShrink: 0,
        boxSizing: 'border-box'
    },
    messageScrollArea: {
        flex: 1,
        padding: '20px 5%',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        gap: '2px',
        backgroundImage: 'url("https://static.whatsapp.net/rsrc.php/v3/yl/r/r_QZ3OAts1Z.png")',
        backgroundSize: 'contain',
        backgroundRepeat: 'repeat',
        backgroundBlendMode: 'overlay',
        backgroundColor: 'rgba(11, 20, 26, 0.96)',
        boxSizing: 'border-box'
    },
    messageBubble: {
        maxWidth: '70%',
        padding: '6px 8px 8px 10px',
        borderRadius: '8px',
        wordBreak: 'break-word',
        fontSize: '14.5px',
        lineHeight: '19px',
        boxShadow: '0 1px 1px rgba(0,0,0,0.1)'
    },
    chatInputArea: {
        padding: '10px 15px',
        backgroundColor: '#202c33',
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        flexShrink: 0,
        boxSizing: 'border-box',
        width: '100%'
    },
    textAreaDynamic: {
        flex: 1,
        backgroundColor: '#2a3942',
        border: 'none',
        borderRadius: '10px',
        padding: '10px 14px',
        color: '#e9edef',
        outline: 'none',
        resize: 'none',
        maxHeight: '120px',
        fontSize: '16px',
        fontFamily: 'inherit',
        boxSizing: 'border-box',
        width: '100%'
    },

    dateDivider: {
        backgroundColor: '#202c33',
        color: '#8696a0',
        fontSize: '12px',
        padding: '6px 12px',
        borderRadius: '8px',
        boxShadow: '0 1px 2px rgba(0,0,0,0.2)',
        textTransform: 'uppercase',
        letterSpacing: '0.5px'
    },
    emptyState: {padding: '30px', textAlign: 'center', color: '#8696a0', fontSize: '14px', lineHeight: '1.6'},
    emptyScreenContainer: {
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100%',
        backgroundColor: '#202c33',
        borderBottom: '8px solid #00a884',
        position: 'relative'
    },

    iconBtn: {
        background: 'transparent',
        border: 'none',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'opacity 0.2s',
        padding: '8px'
    },
    iconBtnSm: {
        background: '#202c33',
        border: 'none',
        cursor: 'pointer',
        width: '36px',
        height: '36px',
        borderRadius: '50%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        transition: 'background 0.2s'
    },

    lightboxBackdrop: {
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100vw',
        height: '100vh',
        backgroundColor: 'rgba(11,20,26,0.95)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: 9999
    },
    lightboxImg: {maxWidth: '90vw', maxHeight: '90vh', objectFit: 'contain'},
    lightboxClose: {
        position: 'absolute', top: '20px', left: '20px', background: 'transparent', border: 'none', cursor: 'pointer'
    },

    callModal: {
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100vw',
        height: '100dvh',
        backgroundColor: '#111b21',
        zIndex: 3000,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        overflow: 'hidden',
        boxSizing: 'border-box'
    },
    connectedCallerCorner: {
        position: 'absolute',
        top: '20px',
        left: '20px',
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        backgroundColor: 'rgba(32,44,51,0.85)',
        padding: '8px 16px',
        borderRadius: '30px',
        backdropFilter: 'blur(5px)',
        zIndex: 10
    },
    callControlsBar: {
        position: 'absolute',
        bottom: '30px',
        left: '50%',
        transform: 'translateX(-50%)',
        display: 'flex',
        gap: '20px',
        backgroundColor: 'rgba(32,44,51,0.9)',
        padding: '12px 28px',
        borderRadius: '50px',
        backdropFilter: 'blur(5px)',
        zIndex: 100,
        boxSizing: 'border-box',
        maxWidth: '90vw',
        boxShadow: '0 8px 32px rgba(0,0,0,0.5)'
    },
    callControlBtn: {
        background: '#2a3942',
        border: 'none',
        borderRadius: '50%',
        width: '50px',
        height: '50px',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        cursor: 'pointer',
        transition: 'all 0.2s ease'
    },

    incomingPopup: {
        position: 'fixed',
        bottom: '30px',
        right: '20px',
        backgroundColor: '#202c33',
        padding: '20px 25px',
        borderRadius: '16px',
        boxShadow: '0 10px 40px rgba(0,0,0,0.6)',
        zIndex: 4000,
        border: '1px solid #2f3b43',
        width: '280px',
        boxSizing: 'border-box'
    },

    avatarOverlayBtn: {
        position: 'absolute',
        bottom: '5px',
        right: '5px',
        backgroundColor: '#00a884',
        borderRadius: '50%',
        padding: '10px',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxShadow: '0 4px 8px rgba(0,0,0,0.4)',
        transition: 'transform 0.2s hover:scale(1.1)'
    },
    profileDetailItem: {
        backgroundColor: '#202c33',
        padding: '15px 20px',
        borderRadius: '12px',
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
        border: '1px solid #2f3b43'
    },
    logoutBtn: {
        width: '100%',
        padding: '16px',
        backgroundColor: 'transparent',
        color: '#ea3323',
        border: '1px solid #ea3323',
        borderRadius: '12px',
        fontWeight: 'bold',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '10px',
        transition: 'all 0.2s ease',
        marginTop: 'auto'
    },

    fileContainer: {
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        padding: '8px',
        minWidth: '200px',
        background: 'rgba(0,0,0,0.15)',
        borderRadius: '6px'
    },
    downloadBtn: {
        backgroundColor: '#00a884',
        borderRadius: '50%',
        width: '34px',
        height: '34px',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        textDecoration: 'none',
        transition: 'transform 0.2s hover:scale(1.05)'
    },

    madeWithLove: {color: '#8696a0', fontSize: '13px', display: 'flex', alignItems: 'center', justifyContent: 'center'},
    link: {color: '#00a884', textDecoration: 'none', marginLeft: '4px', fontWeight: 'bold'}
};