let currentMode = 'ALL';

function setMode(mode) {
    currentMode = mode;
    ['ALL', 'REAL', 'SIMULATED'].forEach(m => {
        const btn = document.getElementById('btnMode' + m);
        btn.className = 'btn btn-sm ' + (m === mode ? 'btn-primary' : 'btn-outline');
    });
    fetchPrediction();
}

async function fetchPrediction() {
    try {
        const res = await fetch('/api/prediction/runoff?mode=' + encodeURIComponent(currentMode));
        if (!res.ok) return;
        const data = await res.json();
        document.getElementById('predNodeLabel').textContent = `Nodo: ${data.servedByNode}`;

        renderSection('nac', data.national);
        renderSection('reg', data.regional);
    } catch (_) {}
}

function renderSection(prefix, pred) {
    if (!pred) return;
    document.getElementById(prefix + 'VerdictSummary').textContent = pred.verdictSummary || '';
    const badge = document.getElementById(prefix + 'VerdictBadge');
    badge.textContent = pred.runoffRequired
        ? `SEGUNDA VUELTA (±${pred.marginOfErrorPct}%)`
        : `GANADOR 1RA VUELTA (±${pred.marginOfErrorPct}%)`;
    badge.className = 'badge ' + (pred.runoffRequired ? 'badge-warn' : 'badge-ok');

    const finalists = pred.finalists || [];
    const vsGrid = document.getElementById(prefix + 'VsGrid');

    if (finalists.length >= 2) {
        const f1 = finalists[0];
        const f2 = finalists[1];
        vsGrid.innerHTML = `
            ${renderFinalistCard(f1, 1, pred.runoffRequired)}
            <div class="vs-badge-wrap"><div class="vs-badge">VS</div></div>
            ${renderFinalistCard(f2, 2, pred.runoffRequired)}
        `;

        document.getElementById(prefix + 'ProbLeftLabel').textContent = `${f1.name} (${f1.winProbabilityPct}%)`;
        document.getElementById(prefix + 'ProbRightLabel').textContent = `${f2.name} (${f2.winProbabilityPct}%)`;

        const leftBar = document.getElementById(prefix + 'ProbLeft');
        const rightBar = document.getElementById(prefix + 'ProbRight');
        leftBar.style.width = Math.max(10, f1.winProbabilityPct) + '%';
        leftBar.textContent = f1.winProbabilityPct + '%';
        rightBar.style.width = Math.max(10, f2.winProbabilityPct) + '%';
        rightBar.textContent = f2.winProbabilityPct + '%';
    }

    const tbody = document.getElementById(prefix + 'RankingBody');
    tbody.innerHTML = (pred.allRankings || []).map((c, idx) => `
        <tr>
            <td><strong>#${idx + 1}</strong></td>
            <td>
                <div style="display: flex; align-items: center; gap: 0.6rem;">
                    <img class="candidate-avatar-sm" src="${escapeHtml(c.photoUrl || '')}" alt="${escapeHtml(c.name)}"
                         onerror="this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(c.name)}&background=20B2A6&color=fff'">
                    <div>
                        <strong>${escapeHtml(c.name)}</strong>
                        <div style="font-size: 0.74rem; color: var(--ink-soft);">${escapeHtml(c.party)}</div>
                    </div>
                </div>
            </td>
            <td><span class="badge badge-real">${Number(c.realVotes).toLocaleString('es-CO')}</span></td>
            <td><span class="badge badge-sim">${Number(c.simulatedVotes).toLocaleString('es-CO')}</span></td>
            <td><strong>${Number(c.votes).toLocaleString('es-CO')}</strong></td>
            <td><strong>${c.currentSharePct}%</strong></td>
            <td>${c.advancesToRunoff ? `<strong>${c.projectedRunoffSharePct}%</strong> (Prob: ${c.winProbabilityPct}%)` : '<span style="color: var(--ink-soft);">—</span>'}</td>
            <td>
                ${c.advancesToRunoff
                    ? '<span class="badge badge-ok">Pasa a 2da Vuelta</span>'
                    : '<span class="badge badge-neutral">Eliminado en 1ra</span>'}
            </td>
        </tr>
    `).join('');
}

function renderFinalistCard(f, rank, runoffRequired) {
    const label = rank === 1
        ? (runoffRequired ? 'FINALISTA #1 (LÍDER)' : 'GANADOR PROYECTADO 1RA VUELTA')
        : 'FINALISTA #2 (RETADOR)';
    return `
        <div class="finalist-card rank-${rank}">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                <span class="badge ${rank === 1 ? 'badge-brand' : 'badge-real'}">${label}</span>
                <span class="ballot-num" style="background: ${f.accentColor};">Tarjetón #${f.ballotNumber}</span>
            </div>
            <div style="display: flex; align-items: center; gap: 0.9rem; margin-bottom: 1rem;">
                <img class="candidate-avatar-lg" src="${escapeHtml(f.photoUrl || '')}" alt="${escapeHtml(f.name)}"
                     onerror="this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(f.name)}&background=20B2A6&color=fff&bold=true'">
                <div>
                    <div style="font-size: 1.25rem; font-weight: 800; color: var(--ink);">${escapeHtml(f.name)}</div>
                    <div style="font-size: 0.84rem; color: var(--ink-soft);">${escapeHtml(f.party)}</div>
                </div>
            </div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem;">
                <div style="background: rgba(255,255,255,0.85); padding: 0.65rem; border-radius: 10px; border: 1px solid var(--border);">
                    <div class="ticket-field-label">Votación Actual</div>
                    <div style="font-size: 1.32rem; font-weight: 800;">${f.currentSharePct}%</div>
                    <div style="font-size: 0.74rem; color: var(--ink-soft);">${Number(f.votes).toLocaleString('es-CO')} votos (${f.realVotes}R / ${f.simulatedVotes}S)</div>
                </div>
                <div style="background: rgba(255,255,255,0.85); padding: 0.65rem; border-radius: 10px; border: 1px solid var(--border);">
                    <div class="ticket-field-label">Proyección Cara a Cara</div>
                    <div style="font-size: 1.32rem; font-weight: 800; color: ${rank === 1 ? '#0f6b63' : '#c2410c'};">${f.projectedRunoffSharePct}%</div>
                    <div style="font-size: 0.74rem; color: var(--ink-soft);">Prob. victoria: <strong>${f.winProbabilityPct}%</strong></div>
                </div>
            </div>
        </div>
    `;
}

function escapeHtml(str) {
    return String(str ?? '').replace(/[&<>"']/g, s => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[s]));
}

fetchPrediction();
setInterval(fetchPrediction, 2500);
