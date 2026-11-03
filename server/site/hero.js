// Rotating hero phrase (every 3s) + flip-card clock.
(function () {
  var em = document.querySelector('.hero h1 em');
  var phrases = em.dataset.phrases.split('|'), i = 0;
  setInterval(function () {
    em.classList.add('out');
    setTimeout(function () { em.textContent = phrases[++i % phrases.length]; em.classList.remove('out'); }, 300);
  }, 3000);

  var clock = document.getElementById('clock'), cards = [];
  for (var k = 0; k < 8; k++) {
    var s = document.createElement('span');
    s.className = k % 3 == 2 ? 'sep' : 'card-d';
    if (k % 3 == 2) s.textContent = ':'; else cards.push(s);
    clock.appendChild(s);
  }
  function tick() {
    var d = new Date(), t = [d.getHours(), d.getMinutes(), d.getSeconds()].map(function (n) { return ('0' + n).slice(-2); }).join('');
    cards.forEach(function (c, n) {
      if (c.textContent == t[n]) return;
      c.textContent = t[n];
      c.classList.remove('flip'); void c.offsetWidth; c.classList.add('flip');
    });
    setTimeout(tick, 1000 - d.getMilliseconds());
  }
  tick();
})();
