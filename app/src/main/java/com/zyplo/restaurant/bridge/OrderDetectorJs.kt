package com.zyplo.restaurant.bridge

object OrderDetectorJs {
    const val SCRIPT = """
(function(){
  if (window.__zyploNativeHooked) return;
  window.__zyploNativeHooked = true;
  function syncSession(){
    try {
      var raw = localStorage.getItem('restaurant_session');
      if (raw && window.ZyploApp && ZyploApp.saveRestaurantSession) ZyploApp.saveRestaurantSession(raw);
      var path = (location.pathname||'').toLowerCase();
      var onLogin = path.indexOf('login') !== -1 || path.indexOf('register') !== -1;
      var logged = !!raw && !onLogin;
      if (window.ZyploApp && ZyploApp.setLoggedIn) ZyploApp.setLoggedIn(!!logged);
    } catch(e) {}
  }
  syncSession();
  setInterval(syncSession, 4000);
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
