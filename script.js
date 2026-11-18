const API_BASE = 'http://127.0.0.1:5000/api';
let schemaConfig = {};
let currentTarget = 'dashboard';

// Initialize App
document.addEventListener('DOMContentLoaded', async () => {
    await fetchSchema();
    setupNavigation();
    setupEvents();
    loadDashboard();
});

// Fetch Schema from Backend
async function fetchSchema() {
    try {
        const response = await fetch(`${API_BASE}/schema`);
        const data = await response.json();
        if (data.success) schemaConfig = data.tables;
    } catch (err) {
        alert("Failed to connect to backend. Is Flask running?");
    }
}

// Sidebar Navigation
function setupNavigation() {
    const links = document.querySelectorAll('#nav-links a');
    links.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            links.forEach(l => l.classList.remove('active'));
            link.classList.add('active');
            
            currentTarget = link.dataset.target;
            document.getElementById('page-title').innerText = link.innerText;
            
            if (currentTarget === 'dashboard') {
                document.getElementById('table-actions').style.display = 'none';
                document.getElementById('table-view').classList.remove('active');
                document.getElementById('dashboard-view').classList.add('active');
                loadDashboard();
            } else {
                document.getElementById('table-actions').style.display = 'flex';
                document.getElementById('dashboard-view').classList.remove('active');
                document.getElementById('table-view').classList.add('active');
                document.getElementById('search-input').value = '';
                loadTable(currentTarget);
            }
        });
    });
}

// Search and Modal Events
function setupEvents() {
    let searchTimeout;
    document.getElementById('search-input').addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => loadTable(currentTarget, e.target.value), 300);
    });

    document.getElementById('add-btn').addEventListener('click', openModal);
    document.getElementById('close-modal').addEventListener('click', closeModal);
    document.getElementById('dynamic-form').addEventListener('submit', handleFormSubmit);
}

// Load Dashboard Analytics
async function loadDashboard() {
    try {
        const res = await fetch(`${API_BASE}/analytics/placement`);
        const data = await res.json();
        if (data.success) {
            document.getElementById('stat-students').innerText = data.data.total_students;
            document.getElementById('stat-placed').innerText = data.data.placed_students;
            document.getElementById('stat-companies').innerText = data.data.total_companies;
            document.getElementById('stat-cgpa').innerText = data.data.average_cgpa;
        }
    } catch (err) {
        console.error(err);
    }
}

// Load Table Data
async function loadTable(table, searchQuery = '') {
    try {
        const url = searchQuery ? `${API_BASE}/${table}?search=${encodeURIComponent(searchQuery)}` : `${API_BASE}/${table}`;
        const res = await fetch(url);
        const data = await res.json();
        if (data.success) renderTable(table, data.data);
    } catch (err) {
        console.error(err);
    }
}

// Render Table UI
function renderTable(tableName, rows) {
    const tableConfig = schemaConfig[tableName];
    const thead = document.getElementById('table-head');
    const tbody = document.getElementById('table-body');
    
    // Headers
    let headerHTML = '<tr>';
    tableConfig.columns.forEach(col => {
        headerHTML += `<th>${col.replace(/_/g, ' ')}</th>`;
    });
    headerHTML += '<th>Actions</th></tr>';
    thead.innerHTML = headerHTML;

    // Rows
    tbody.innerHTML = '';
    if (rows.length === 0) {
        tbody.innerHTML = `<tr><td colspan="${tableConfig.columns.length + 1}" style="text-align:center;">No records found.</td></tr>`;
        return;
    }

    rows.forEach(row => {
        let tr = document.createElement('tr');
        tableConfig.columns.forEach(col => {
            let td = document.createElement('td');
            td.innerText = row[col] !== null ? row[col] : '-';
            tr.appendChild(td);
        });
        
        let actionTd = document.createElement('td');
        let deleteBtn = document.createElement('button');
        deleteBtn.className = 'danger-btn';
        deleteBtn.innerText = 'Delete';
        deleteBtn.onclick = () => deleteRecord(tableName, row[tableConfig.pk]);
        actionTd.appendChild(deleteBtn);
        
        tr.appendChild(actionTd);
        tbody.appendChild(tr);
    });
}

// Dynamically Build Form Modal
function openModal() {
    const tableConfig = schemaConfig[currentTarget];
    const formFields = document.getElementById('form-fields');
    formFields.innerHTML = '';

    tableConfig.columns.forEach(col => {
        if (tableConfig.auto_pk && col === tableConfig.pk) return; // Skip ID if auto-increment
        
        const isRequired = tableConfig.required.includes(col);
        const inputType = col.includes('date') || col.includes('deadline') ? 'date' : 'text';

        formFields.innerHTML += `
            <div class="form-group">
                <label>${col.replace(/_/g, ' ')}${isRequired ? ' *' : ''}</label>
                <input type="${inputType}" name="${col}" ${isRequired ? 'required' : ''}>
            </div>
        `;
    });

    document.getElementById('form-modal').style.display = 'flex';
}

function closeModal() {
    document.getElementById('form-modal').style.display = 'none';
    document.getElementById('dynamic-form').reset();
}

// Submit Form (POST)
async function handleFormSubmit(e) {
    e.preventDefault();
    const formData = new FormData(e.target);
    const payload = Object.fromEntries(formData.entries());

    try {
        const res = await fetch(`${API_BASE}/${currentTarget}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const result = await res.json();
        
        if (result.success) {
            closeModal();
            loadTable(currentTarget);
        } else {
            alert("Error: " + result.error);
        }
    } catch (err) {
        alert("Network Error");
    }
}

// Delete Record
async function deleteRecord(table, id) {
    if (!confirm(`Are you sure you want to delete this record (ID: ${id})?`)) return;

    try {
        const res = await fetch(`${API_BASE}/${table}/${id}`, { method: 'DELETE' });
        const result = await res.json();
        
        if (result.success) {
            loadTable(table);
            loadDashboard(); // Refresh counts
        } else {
            alert("Error: " + result.error);
        }
    } catch (err) {
        alert("Network Error");
    }
}