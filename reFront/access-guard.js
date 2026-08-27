(() => {
  const indexUrl = new URL("./index.html", window.location.href);
  let cameFromIndex = false;

  try {
    const referrerUrl = document.referrer ? new URL(document.referrer) : null;
    cameFromIndex = Boolean(
      referrerUrl &&
      referrerUrl.origin === indexUrl.origin &&
      referrerUrl.pathname === indexUrl.pathname
    );
  } catch {
    cameFromIndex = false;
  }

  if (!cameFromIndex) {
    window.location.replace(indexUrl.href);
  }
})();
