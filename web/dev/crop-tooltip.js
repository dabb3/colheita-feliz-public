const timeFormat = new Intl.DateTimeFormat("pt-BR", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

for (const label of document.querySelectorAll("[data-countdown]")) {
  const [hours, minutes] = label.dataset.countdown.split(":").map(Number);
  label.dataset.remaining = String(hours * 3600 + minutes * 60);
}

function updateTimes() {
  for (const label of document.querySelectorAll("[data-countdown]")) {
    const remaining = Math.max(0, Number(label.dataset.remaining) - 1);
    label.dataset.remaining = String(remaining);
    if (remaining < 60 || remaining % 60 === 0) {
      const hours = Math.floor(remaining / 3600);
      const minutes = Math.floor((remaining % 3600) / 60);
      label.textContent = remaining < 60 ? `${remaining}s` : hours ? `${hours}h ${minutes}min` : `${minutes}min`;
    }
  }

  const now = new Date();
  for (const time of document.querySelectorAll("[data-ready]")) {
    const remaining = Number(time.closest(".crop-hud").querySelector("[data-countdown]").dataset.remaining);
    const ready = new Date(now.getTime() + remaining * 1000);
    time.textContent = timeFormat.format(ready).replace(":", "h");
  }
}

updateTimes();
window.setInterval(updateTimes, 1000);
