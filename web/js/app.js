// Student Placement Management System - Frontend Vanilla JavaScript

let currentUser = null;
let cachedStudents = [];
let cachedDrives = [];
let cachedCompanies = [];

document.addEventListener('DOMContentLoaded', () => {
    initAuth();
    setupNavigation();
    setupForms();
});

// --- Authentication ---
function initAuth() {
    const saved = sessionStorage.getItem('placement_user');
    if (saved) {
        try {
            currentUser = JSON.parse(saved);
            updateUserDisplay();
            hideLogin();
            loadAllData();
        } catch (e) {
            showLogin();
        }
    } else {
        showLogin();
    }

    document.getElementById('loginForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('loginUsername').value.trim();
        const password = document.getElementById('loginPassword').value.trim();
        const alertBox = document.getElementById('loginAlert');
        const btnSignIn = document.getElementById('btnSignIn');
        const btnText = document.getElementById('signInBtnText');

        alertBox.style.display = 'none';
        btnSignIn.disabled = true;
        btnText.textContent = 'Verifying with Oracle DB...';

        try {
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await res.json();

            if (res.ok && data.success) {
                currentUser = data.user;
                sessionStorage.setItem('placement_user', JSON.stringify(currentUser));
                updateUserDisplay();
                hideLogin();
                await loadAllData();
            } else {
                alertBox.className = 'alert-feedback alert-error';
                alertBox.textContent = data.message || 'Invalid username or password.';
                alertBox.style.display = 'block';
            }
        } catch (err) {
            alertBox.className = 'alert-feedback alert-error';
            alertBox.textContent = 'Server connection error: ' + err.message;
            alertBox.style.display = 'block';
        } finally {
            btnSignIn.disabled = false;
            btnText.textContent = 'Sign In to Dashboard →';
        }
    });

    document.getElementById('btnLogout').addEventListener('click', () => {
        sessionStorage.removeItem('placement_user');
        currentUser = null;
        showLogin();
    });
}

function showLogin() {
    document.getElementById('loginOverlay').style.display = 'flex';
}

function hideLogin() {
    document.getElementById('loginOverlay').style.display = 'none';
}

function updateUserDisplay() {
    if (currentUser) {
        const nameEl = document.getElementById('userNameDisplay');
        const roleEl = document.getElementById('userRoleDisplay');
        const avatarEl = document.getElementById('userAvatar');

        const name = currentUser.fullName || currentUser.username;
        if (nameEl) nameEl.textContent = name;
        if (roleEl) roleEl.textContent = currentUser.role || 'OFFICER';
        if (avatarEl) avatarEl.textContent = name.charAt(0).toUpperCase();
    }
}

// --- Navigation Tabs ---
function setupNavigation() {
    const tabs = document.querySelectorAll('.nav-tab-link');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const target = tab.getAttribute('data-tab');
            switchTab(target);
        });
    });
}

async function switchTab(tabId) {
    document.querySelectorAll('.nav-tab-link').forEach(t => {
        t.classList.toggle('active', t.getAttribute('data-tab') === tabId);
    });
    document.querySelectorAll('.tab-pane').forEach(c => {
        c.classList.toggle('active', c.id === tabId);
    });

    if (tabId === 'tab-dashboard') await loadDashboard();
    if (tabId === 'tab-students') await loadStudents();
    if (tabId === 'tab-drives') { await loadCompanies(); await loadDrives(); }
    if (tabId === 'tab-applications' || tabId === 'tab-eligibility') {
        await ensureDropdownDataLoaded();
        populateSelectOptions();
    }
    if (tabId === 'tab-view-apps') await loadApplications();
    if (tabId === 'tab-database') await checkDbHealth();
}

async function ensureDropdownDataLoaded() {
    if (cachedStudents.length === 0) {
        await loadStudents();
    }
    if (cachedDrives.length === 0) {
        await loadDrives();
    }
}

// --- Data Loading ---
async function loadAllData() {
    await Promise.all([
        loadDashboard(),
        loadStudents(),
        loadCompanies(),
        loadDrives(),
        loadApplications()
    ]);
    populateSelectOptions();
    await checkDbHealth();
}

async function loadDashboard() {
    try {
        const res = await fetch('/api/dashboard');
        const data = await res.json();
        document.getElementById('statStudents').textContent = data.totalStudents || 0;
        document.getElementById('statPlaced').textContent = data.placedStudents || 0;
        document.getElementById('statCompanies').textContent = data.totalCompanies || 0;
        document.getElementById('statApplications').textContent = data.totalApplications || 0;

        // Update nav badges
        const sBadge = document.getElementById('navBadgeStudents');
        const aBadge = document.getElementById('navBadgeApps');
        if (sBadge) sBadge.textContent = data.totalStudents || 0;
        if (aBadge) aBadge.textContent = data.totalApplications || 0;
    } catch (err) {
        console.error('Failed to load dashboard:', err);
    }
}

async function loadStudents() {
    try {
        const res = await fetch('/api/students');
        cachedStudents = await res.json();
        const tbody = document.querySelector('#studentsTable tbody');
        if (!tbody) return;
        tbody.innerHTML = '';

        const sBadge = document.getElementById('navBadgeStudents');
        if (sBadge) sBadge.textContent = cachedStudents.length || 0;

        if (!Array.isArray(cachedStudents) || cachedStudents.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9" style="text-align: center; color: #64748b; padding: 24px;">No student records found in Oracle database.</td></tr>';
            populateSelectOptions();
            return;
        }

        cachedStudents.forEach(s => {
            const tr = document.createElement('tr');
            const statusBadge = s.status === 'PLACED' 
                ? '<span class="badge-pill badge-success">PLACED</span>' 
                : '<span class="badge-pill badge-info">UNPLACED</span>';

            tr.innerHTML = `
                <td><strong>#${s.id}</strong></td>
                <td><code>${escapeHtml(s.registerNo)}</code></td>
                <td><strong style="color: var(--navy-900);">${escapeHtml(s.name)}</strong></td>
                <td>${escapeHtml(s.department)}</td>
                <td><strong style="color: var(--teal-700);">${s.cgpa.toFixed(2)}</strong></td>
                <td>${s.backlogs}</td>
                <td>${escapeHtml(s.email)}</td>
                <td>${escapeHtml(s.phone || '-')}</td>
                <td>${statusBadge}</td>
            `;
            tbody.appendChild(tr);
        });

        populateSelectOptions();
    } catch (err) {
        console.error('Error loading students:', err);
    }
}

async function loadCompanies() {
    try {
        const res = await fetch('/api/companies');
        cachedCompanies = await res.json();
        const select = document.getElementById('driveCompanySelect');
        if (!select) return;
        select.innerHTML = '<option value="">-- Select Company --</option>';

        if (Array.isArray(cachedCompanies) && cachedCompanies.length > 0) {
            cachedCompanies.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = `${c.name} (${c.industry} - ${c.location})`;
                select.appendChild(opt);
            });
        }
    } catch (err) {
        console.error('Error loading companies:', err);
    }
}

async function loadDrives() {
    try {
        const res = await fetch('/api/drives');
        cachedDrives = await res.json();
        const tbody = document.querySelector('#drivesTable tbody');
        if (!tbody) return;
        tbody.innerHTML = '';

        const dBadge = document.getElementById('navBadgeDrives');
        if (dBadge) dBadge.textContent = cachedDrives.length || 0;

        if (!Array.isArray(cachedDrives) || cachedDrives.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #64748b; padding: 24px;">No placement drives scheduled yet.</td></tr>';
            populateSelectOptions();
            return;
        }

        cachedDrives.forEach(d => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>#${d.id}</strong></td>
                <td><strong style="color: var(--navy-900);">${escapeHtml(d.companyName)}</strong></td>
                <td>${escapeHtml(d.jobRole)}</td>
                <td><strong style="color: var(--teal-700);">${d.packageLpa.toFixed(2)} LPA</strong></td>
                <td>${d.minCgpa.toFixed(2)}</td>
                <td>${d.maxBacklogs}</td>
                <td><code>${d.driveDate}</code></td>
                <td><span class="badge-pill badge-success">${d.status}</span></td>
            `;
            tbody.appendChild(tr);
        });

        populateSelectOptions();
    } catch (err) {
        console.error('Error loading drives:', err);
    }
}

async function loadApplications() {
    try {
        const res = await fetch('/api/applications');
        const apps = await res.json();
        const tbody = document.querySelector('#applicationsTable tbody');
        if (!tbody) return;
        tbody.innerHTML = '';

        const aBadge = document.getElementById('navBadgeApps');
        if (aBadge) aBadge.textContent = apps.length || 0;

        if (!Array.isArray(apps) || apps.length === 0) {
            tbody.innerHTML = '<tr><td colspan="12" style="text-align: center; color: #64748b; padding: 24px;">No applications recorded in Oracle database.</td></tr>';
            return;
        }

        apps.forEach(a => {
            const tr = document.createElement('tr');
            
            let eligBadge = a.eligibilityStatus === 'ELIGIBLE' 
                ? '<span class="badge-pill badge-success">ELIGIBLE</span>' 
                : '<span class="badge-pill badge-danger">NOT ELIGIBLE</span>';

            let statusBadge = '<span class="badge-pill badge-info">' + a.status + '</span>';
            if (a.status === 'PLACED') statusBadge = '<span class="badge-pill badge-success">PLACED</span>';
            if (a.status === 'REJECTED') statusBadge = '<span class="badge-pill badge-danger">REJECTED</span>';
            if (a.status === 'SHORTLISTED') statusBadge = '<span class="badge-pill badge-warning">SHORTLISTED</span>';

            tr.innerHTML = `
                <td><strong>#${a.applicationId}</strong></td>
                <td><strong style="color: var(--navy-900);">${escapeHtml(a.studentName)}</strong></td>
                <td><code>${escapeHtml(a.regNo)}</code></td>
                <td><strong>${a.studentCgpa.toFixed(2)}</strong></td>
                <td>${escapeHtml(a.companyName)}</td>
                <td>${escapeHtml(a.jobRole)}</td>
                <td><strong style="color: var(--teal-700);">${a.packageLpa.toFixed(2)} LPA</strong></td>
                <td>${a.minCgpa.toFixed(2)}</td>
                <td>${eligBadge}</td>
                <td>${statusBadge}</td>
                <td><small style="color: var(--slate-500);">${escapeHtml(a.remarks || '-')}</small></td>
                <td>
                    <select onchange="updateApplicationStatus(${a.applicationId}, this.value)" style="font-size: 0.78rem; padding: 5px 8px; border-radius: 4px; border: 1px solid var(--slate-300);">
                        <option value="">Status...</option>
                        <option value="SHORTLISTED">Shortlist</option>
                        <option value="PLACED">Mark Placed</option>
                        <option value="REJECTED">Reject</option>
                    </select>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        console.error('Error loading applications:', err);
    }
}

function populateSelectOptions() {
    // Populate Student dropdowns in Eligibility and Apply tabs
    const eligStudent = document.getElementById('eligStudentSelect');
    const applyStudent = document.getElementById('applyStudentSelect');

    if (eligStudent && applyStudent) {
        const curEligStu = eligStudent.value;
        const curApplyStu = applyStudent.value;

        eligStudent.innerHTML = '';
        applyStudent.innerHTML = '';

        if (!cachedStudents || cachedStudents.length === 0) {
            eligStudent.innerHTML = '<option value="">-- No Students Registered --</option>';
            applyStudent.innerHTML = '<option value="">-- No Students Registered --</option>';
        } else {
            eligStudent.innerHTML = '<option value="">-- Choose Student --</option>';
            applyStudent.innerHTML = '<option value="">-- Select Student --</option>';
            cachedStudents.forEach(s => {
                const text = `${s.name} (${s.registerNo} - CGPA: ${s.cgpa.toFixed(2)})`;
                eligStudent.add(new Option(text, s.id));
                applyStudent.add(new Option(text, s.id));
            });
            if (curEligStu) eligStudent.value = curEligStu;
            if (curApplyStu) applyStudent.value = curApplyStu;
        }
    }

    // Populate Drive dropdowns
    const eligDrive = document.getElementById('eligDriveSelect');
    const applyDrive = document.getElementById('applyDriveSelect');

    if (eligDrive && applyDrive) {
        const curEligDrv = eligDrive.value;
        const curApplyDrv = applyDrive.value;

        eligDrive.innerHTML = '';
        applyDrive.innerHTML = '';

        if (!cachedDrives || cachedDrives.length === 0) {
            eligDrive.innerHTML = '<option value="">-- No Placement Drives Available --</option>';
            applyDrive.innerHTML = '<option value="">-- No Placement Drives Available --</option>';
        } else {
            eligDrive.innerHTML = '<option value="">-- Choose Drive --</option>';
            applyDrive.innerHTML = '<option value="">-- Select Drive --</option>';
            cachedDrives.forEach(d => {
                const text = `${d.companyName} - ${d.jobRole} (Min CGPA: ${d.minCgpa.toFixed(2)}, ${d.packageLpa} LPA)`;
                eligDrive.add(new Option(text, d.id));
                applyDrive.add(new Option(text, d.id));
            });
            if (curEligDrv) eligDrive.value = curEligDrv;
            if (curApplyDrv) applyDrive.value = curApplyDrv;
        }
    }
}

// --- Eligibility Evaluation ---
async function checkEligibility() {
    const studentId = document.getElementById('eligStudentSelect').value;
    const driveId = document.getElementById('eligDriveSelect').value;
    const resBox = document.getElementById('eligibilityResult');

    if (!studentId || !driveId) {
        alert('Please choose both a student and a placement drive from the dropdowns.');
        return;
    }

    try {
        const res = await fetch(`/api/check-eligibility?studentId=${studentId}&driveId=${driveId}`);
        const data = await res.json();

        resBox.style.display = 'block';
        if (data.isEligible) {
            resBox.className = 'eligibility-verdict-box eligible';
            document.getElementById('eligStatusTitle').textContent = '✅ ELIGIBILITY VERDICT: CANDIDATE IS ELIGIBLE';
        } else {
            resBox.className = 'eligibility-verdict-box not-eligible';
            document.getElementById('eligStatusTitle').textContent = '❌ ELIGIBILITY VERDICT: CANDIDATE NOT ELIGIBLE';
        }

        document.getElementById('eligReason').textContent = data.reason;
        document.getElementById('eligDetails').innerHTML = `
            <strong>Metric Comparison:</strong> Student CGPA: <code>${data.studentCgpa.toFixed(2)}</code> vs Min Required: <code>${data.minCgpa.toFixed(2)}</code> &bull; 
            Student Backlogs: <code>${data.studentBacklogs}</code> vs Max Allowed: <code>${data.maxBacklogs}</code>
        `;
    } catch (err) {
        alert('Failed to evaluate eligibility: ' + err.message);
    }
}

// --- Status Update for Applications ---
async function updateApplicationStatus(applicationId, newStatus) {
    if (!newStatus) return;
    try {
        const res = await fetch('/api/applications', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                action: 'update_status',
                applicationId: applicationId,
                status: newStatus,
                remarks: `Status updated by ${currentUser ? currentUser.fullName : 'Placement Officer'}`
            })
        });
        const data = await res.json();
        if (data.success) {
            await loadApplications();
            await loadStudents();
            await loadDashboard();
        } else {
            alert('Failed to update application: ' + data.message);
        }
    } catch (e) {
        alert('Error updating status: ' + e.message);
    }
}

// --- Forms Setup ---
function setupForms() {
    // Student Form
    document.getElementById('studentForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const alertBox = document.getElementById('studentAlert');
        alertBox.style.display = 'none';

        const payload = {
            registerNo: document.getElementById('stuRegNo').value.trim(),
            name: document.getElementById('stuName').value.trim(),
            department: document.getElementById('stuDept').value,
            cgpa: document.getElementById('stuCgpa').value,
            backlogs: document.getElementById('stuBacklogs').value,
            email: document.getElementById('stuEmail').value.trim(),
            phone: document.getElementById('stuPhone').value.trim()
        };

        try {
            const res = await fetch('/api/students', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();

            if (res.ok && data.success) {
                alertBox.className = 'alert-feedback alert-success';
                alertBox.textContent = data.message;
                alertBox.style.display = 'block';
                document.getElementById('studentForm').reset();
                await loadStudents();
                await loadDashboard();
            } else {
                alertBox.className = 'alert-feedback alert-error';
                alertBox.textContent = data.message || 'Error inserting student record.';
                alertBox.style.display = 'block';
            }
        } catch (err) {
            alertBox.className = 'alert-feedback alert-error';
            alertBox.textContent = 'Server error: ' + err.message;
            alertBox.style.display = 'block';
        }
    });

    // Company Form
    document.getElementById('companyForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const alertBox = document.getElementById('companyAlert');
        alertBox.style.display = 'none';

        const payload = {
            name: document.getElementById('compName').value.trim(),
            industry: document.getElementById('compIndustry').value.trim(),
            location: document.getElementById('compLocation').value.trim(),
            hrEmail: document.getElementById('compEmail').value.trim(),
            hrPhone: document.getElementById('compPhone').value.trim(),
            website: document.getElementById('compWebsite').value.trim()
        };

        try {
            const res = await fetch('/api/companies', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();

            if (res.ok && data.success) {
                alertBox.className = 'alert-feedback alert-success';
                alertBox.textContent = data.message;
                alertBox.style.display = 'block';
                document.getElementById('companyForm').reset();
                await loadCompanies();
                await loadDashboard();
            } else {
                alertBox.className = 'alert-feedback alert-error';
                alertBox.textContent = data.message;
                alertBox.style.display = 'block';
            }
        } catch (err) {
            alertBox.className = 'alert-feedback alert-error';
            alertBox.textContent = 'Server error: ' + err.message;
            alertBox.style.display = 'block';
        }
    });

    // Drive Form
    document.getElementById('driveForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const alertBox = document.getElementById('driveAlert');
        alertBox.style.display = 'none';

        const payload = {
            companyId: document.getElementById('driveCompanySelect').value,
            jobRole: document.getElementById('driveRole').value.trim(),
            packageLpa: document.getElementById('drivePackage').value,
            minCgpa: document.getElementById('driveMinCgpa').value,
            maxBacklogs: document.getElementById('driveMaxBacklogs').value,
            driveDate: document.getElementById('driveDate').value
        };

        try {
            const res = await fetch('/api/drives', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();

            if (res.ok && data.success) {
                alertBox.className = 'alert-feedback alert-success';
                alertBox.textContent = data.message;
                alertBox.style.display = 'block';
                document.getElementById('driveForm').reset();
                await loadDrives();
                await loadDashboard();
            } else {
                alertBox.className = 'alert-feedback alert-error';
                alertBox.textContent = data.message;
                alertBox.style.display = 'block';
            }
        } catch (err) {
            alertBox.className = 'alert-feedback alert-error';
            alertBox.textContent = 'Server error: ' + err.message;
            alertBox.style.display = 'block';
        }
    });

    // Application Form
    document.getElementById('applyForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const alertBox = document.getElementById('appAlert');
        alertBox.style.display = 'none';

        const studentId = document.getElementById('applyStudentSelect').value;
        const driveId = document.getElementById('applyDriveSelect').value;

        if (!studentId || !driveId) {
            alertBox.className = 'alert-feedback alert-error';
            alertBox.textContent = 'Please select both a student and a placement drive.';
            alertBox.style.display = 'block';
            return;
        }

        const payload = {
            studentId: studentId,
            driveId: driveId,
            remarks: document.getElementById('applyRemarks').value.trim()
        };

        try {
            const res = await fetch('/api/applications', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();

            if (res.ok && data.success) {
                alertBox.className = 'alert-feedback alert-success';
                alertBox.textContent = data.message;
                alertBox.style.display = 'block';
                document.getElementById('applyForm').reset();
                await loadApplications();
                await loadDashboard();
            } else {
                alertBox.className = 'alert-feedback alert-error';
                alertBox.textContent = data.message;
                alertBox.style.display = 'block';
            }
        } catch (err) {
            alertBox.className = 'alert-feedback alert-error';
            alertBox.textContent = 'Server error: ' + err.message;
            alertBox.style.display = 'block';
        }
    });
}

// --- Reorganized SQL Demonstrator ---
function switchSqlTab(sectionId) {
    document.querySelectorAll('.sql-cat-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    if (window.event && window.event.currentTarget) {
        window.event.currentTarget.classList.add('active');
    }

    document.querySelectorAll('.sql-module-panel').forEach(sec => {
        sec.classList.remove('active');
    });
    const targetSec = document.getElementById(sectionId);
    if (targetSec) targetSec.classList.add('active');
}

function loadSqlPreset(sql) {
    const editor = document.getElementById('sqlInput');
    editor.value = sql;
    editor.focus();
    editor.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function clearSqlEditor() {
    document.getElementById('sqlInput').value = '';
    const resBox = document.getElementById('sqlResultBox');
    if (resBox) resBox.style.display = 'none';
}

async function executeUserSql() {
    const sql = document.getElementById('sqlInput').value.trim();
    const resultBox = document.getElementById('sqlResultBox');
    const msgBox = document.getElementById('sqlMessage');
    const thead = document.getElementById('sqlResultThead');
    const tbody = document.getElementById('sqlResultTbody');
    const btnRun = document.getElementById('btnRunSql');

    if (!sql) {
        alert('Please select a preset query above or enter an SQL statement first.');
        return;
    }

    resultBox.style.display = 'block';
    thead.innerHTML = '';
    tbody.innerHTML = '';
    msgBox.style.display = 'none';
    btnRun.disabled = true;
    btnRun.textContent = 'Executing Query...';

    try {
        const res = await fetch('/api/query', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ query: sql })
        });
        const data = await res.json();

        if (data.success) {
            msgBox.className = 'alert-feedback alert-success';
            msgBox.textContent = data.message;
            msgBox.style.display = 'block';

            if (data.type === 'DQL' && data.columns && data.rows) {
                const trHead = document.createElement('tr');
                data.columns.forEach(col => {
                    const th = document.createElement('th');
                    th.textContent = col;
                    trHead.appendChild(th);
                });
                thead.appendChild(trHead);

                if (data.rows.length === 0) {
                    tbody.innerHTML = `<tr><td colspan="${data.columns.length}" style="text-align: center; color: #64748b; padding: 18px;">Query returned 0 rows.</td></tr>`;
                } else {
                    data.rows.forEach(row => {
                        const tr = document.createElement('tr');
                        row.forEach(val => {
                            const td = document.createElement('td');
                            td.textContent = val !== null ? val : 'NULL';
                            tr.appendChild(td);
                        });
                        tbody.appendChild(tr);
                    });
                }
            }

            // Refresh data state if DML or DDL altered database tables
            if (data.type === 'DML' || data.type === 'DDL') {
                await loadAllData();
            }
        } else {
            msgBox.className = 'alert-feedback alert-error';
            msgBox.textContent = data.message || 'Error executing query.';
            msgBox.style.display = 'block';
        }
    } catch (err) {
        msgBox.className = 'alert-feedback alert-error';
        msgBox.textContent = 'Server connection error: ' + err.message;
        msgBox.style.display = 'block';
    } finally {
        btnRun.disabled = false;
        btnRun.textContent = 'Execute SQL Query →';
    }
}

// --- Database Connectivity Health ---
async function checkDbHealth() {
    const statusText = document.getElementById('dbStatusText');
    const detailsText = document.getElementById('dbDetailsText');
    const dot = document.getElementById('dbStatusDot');

    try {
        const res = await fetch('/api/health');
        const data = await res.json();

        if (res.ok && data.status === 'CONNECTED') {
            statusText.textContent = 'JDBC Connection: ACTIVE & HEALTHY';
            statusText.style.color = 'var(--teal-700)';
            if (dot) {
                dot.style.background = 'var(--teal-600)';
                dot.style.boxShadow = '0 0 0 4px var(--teal-100)';
            }
            detailsText.innerHTML = `
                Connected to Oracle Database XE &bull; Schema: <code>${escapeHtml(data.user)}</code> &bull; Product Version: <code>${escapeHtml(data.version)}</code> &bull; Server Port: <code>${data.port}</code>
            `;
        } else {
            statusText.textContent = 'JDBC Connection: FAILED';
            statusText.style.color = 'var(--danger-text)';
            if (dot) {
                dot.style.background = 'var(--danger-text)';
                dot.style.boxShadow = '0 0 0 4px var(--danger-border)';
            }
            detailsText.textContent = 'Error: ' + (data.error || 'Unable to connect to Oracle XE database.');
        }
    } catch (err) {
        statusText.textContent = 'Server Unreachable';
        statusText.style.color = 'var(--danger-text)';
        if (dot) {
            dot.style.background = 'var(--danger-text)';
            dot.style.boxShadow = '0 0 0 4px var(--danger-border)';
        }
        detailsText.textContent = 'Cannot reach backend server: ' + err.message;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
