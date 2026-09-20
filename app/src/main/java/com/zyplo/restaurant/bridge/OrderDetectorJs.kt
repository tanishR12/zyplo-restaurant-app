package com.zyplo.restaurant.bridge

object OrderDetectorJs {
    const val SCRIPT = """
(function(){
  function restore(){
    try {
      var raw = localStorage.getItem('restaurant_session');
      if ((!raw || raw === 'null' || raw === '{}') && window.ZyploApp && ZyploApp.restaurantSession) {
        var nativeSession = ZyploApp.restaurantSession();
        if (nativeSession) {
          localStorage.setItem('restaurant_session', nativeSession);
          raw = nativeSession;
        }
      }
      if (raw && window.ZyploApp && ZyploApp.saveRestaurantSession) ZyploApp.saveRestaurantSession(raw);
      if (raw && window.ZyploApp && ZyploApp.setLoggedIn) ZyploApp.setLoggedIn(true);
    } catch(e) {}
  }
  restore();
  if (window.__zyploNativeHooked) return;
  window.__zyploNativeHooked = true;
  setInterval(restore, 4000);
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
