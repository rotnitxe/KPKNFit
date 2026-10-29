(() => {
  const scenes = window.KPKN_SCENARIOS || [];
  const groups = [...new Set(scenes.map((scene) => scene.group))];
  const filters = document.querySelector('#group-filters');
  const host = document.querySelector('#scenario-groups');
  const search = document.querySelector('#scene-search');
  const count = document.querySelector('#result-count');
  if (!filters || !host || !search) return;

  const safe = (value) => String(value).replace(/[&<>"']/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const filterNames = ['Todas', ...groups];
  filters.innerHTML = filterNames.map((name, index) => `<button class="filter-button" type="button" data-filter="${safe(index === 0 ? '*' : name)}" aria-pressed="${index === 0}">${safe(name)}</button>`).join('');
  let activeGroup = '*';

  function render() {
    const query = search.value.trim().toLocaleLowerCase('es');
    const visible = scenes.filter((scene) => {
      const groupMatch = activeGroup === '*' || scene.group === activeGroup;
      const haystack = `${scene.title} ${scene.detail} ${scene.metric} ${scene.action} ${scene.group}`.toLocaleLowerCase('es');
      return groupMatch && haystack.includes(query);
    });
    const grouped = new Map();
    for (const scene of visible) {
      if (!grouped.has(scene.group)) grouped.set(scene.group, []);
      grouped.get(scene.group).push(scene);
    }
    if (!visible.length) {
      host.innerHTML = '<p class="empty-results">No hay escenarios que coincidan. Prueba otro término.</p>';
    } else {
      let ordinal = 1;
      host.innerHTML = [...grouped.entries()].map(([group, items]) => {
        const rows = items.map((scene) => {
          const number = String(ordinal++).padStart(2, '0');
          const label = scene.projection === false ? '<span class="projection-label">Flujo interactivo</span>' : '<span class="projection-label">Proyección · maqueta</span>';
          return `<a class="scenario-link" href="demo.html#${encodeURIComponent(scene.id)}"><span class="scenario-index" aria-hidden="true">${number}</span><span class="scenario-copy"><h3>${safe(scene.title)}</h3><p>${safe(scene.detail)}</p>${label}</span><span class="scenario-metric">${safe(scene.metric)}<span class="scenario-arrow" aria-hidden="true">→</span></span></a>`;
        }).join('');
        return `<section class="scenario-group" aria-labelledby="group-${safe(group).replace(/[^a-z0-9]+/gi,'-')}"><div class="group-heading"><h2 id="group-${safe(group).replace(/[^a-z0-9]+/gi,'-')}">${safe(group)}</h2><span>${items.length} ${items.length === 1 ? 'pantalla' : 'pantallas'}</span></div><div class="scenario-grid">${rows}</div></section>`;
      }).join('');
    }
    count.textContent = `${visible.length} ${visible.length === 1 ? 'escenario' : 'escenarios'}`;
  }

  filters.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-filter]');
    if (!button) return;
    activeGroup = button.dataset.filter;
    for (const item of filters.querySelectorAll('button[data-filter]')) item.setAttribute('aria-pressed', String(item === button));
    render();
  });
  search.addEventListener('input', render);
  document.addEventListener('keydown', (event) => {
    if (event.key === '/' && !['INPUT','TEXTAREA','SELECT'].includes(document.activeElement?.tagName)) {
      event.preventDefault(); search.focus();
    }
  });
  render();
})();
