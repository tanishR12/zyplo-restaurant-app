package com.zyplo.restaurant.bridge

object OrderDetectorJs {
    const val SCRIPT = """
(function(){
  if (window.__zyploNativeHooked) return;
  window.__zyploNativeHooked = true;
  function markLoggedIn(){
    try {
      var path = (location.pathname||'').toLowerCase();
      var session = false;
      try { session = !!localStorage.getItem('restaurant_session'); } catch(e) {}
      var onLogin = path.indexOf('login') !== -1 || path.indexOf('register') !== -1;
      var logged = session && !onLogin;
      if (window.ZyploApp && ZyploApp.setLoggedIn) ZyploApp.setLoggedIn(!!logged);
    } catch(e) {}
  }
  markLoggedIn();
  setInterval(markLoggedIn, 4000);
  try {
    var token = ZyploApp.fcmToken();
    if (token) {
      window.localStorage.setItem('zyplo_fcm_token', token);
      window.localStorage.setItem('zyplo_native_platform', 'android_restaurant');
      window.dispatchEvent(new CustomEvent('zyplo-fcm-token', {detail: token}));
    }
  } catch(e) {}
})();
"""
}
