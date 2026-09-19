package com.zyplo.restaurant.bridge

object WebCompatJs {
    const val SCRIPT = """
(function(){
  try {
    var token = '';
    try { token = (window.ZyploApp && ZyploApp.fcmToken && ZyploApp.fcmToken()) || ''; } catch(e) {}
    if (token) {
      localStorage.setItem('zyplo_fcm_token', token);
      localStorage.setItem('zyplo_pending_fcm_token_v1', token);
      localStorage.setItem('zyplo_native_platform', 'android_restaurant');
      window.__ZYLO_NATIVE_FCM = token;
      window.dispatchEvent(new CustomEvent('zyplo-fcm-token', {detail: token}));
    }

    if (!window.__zyploWebCompat) {
      window.__zyploWebCompat = true;
      if (!window.Notification) {
        window.Notification = function(title, opts){ this.title=title; this.body=(opts&&opts.body)||''; };
      }
      try {
        Object.defineProperty(window.Notification, 'permission', {configurable:true, get:function(){ return 'granted'; }});
      } catch(e) { window.Notification.permission = 'granted'; }
      window.Notification.requestPermission = function(){ return Promise.resolve('granted'); };

      if (navigator.permissions && navigator.permissions.query) {
        var origQuery = navigator.permissions.query.bind(navigator.permissions);
        navigator.permissions.query = function(desc){
          var name = desc && desc.name;
          if (name === 'notifications' || name === 'geolocation' || name === 'camera' || name === 'microphone') {
            return Promise.resolve({state:'granted', status:'granted', onchange:null});
          }
          return origQuery(desc);
        };
      }

      if (navigator.geolocation && window.ZyploApp && ZyploApp.locationJson) {
        var origGet = navigator.geolocation.getCurrentPosition.bind(navigator.geolocation);
        navigator.geolocation.getCurrentPosition = function(ok, err, opts){
          origGet(ok, function(e){
            try {
              var loc = JSON.parse(ZyploApp.locationJson() || '{}');
              if (loc.lat && loc.lng && ok) {
                ok({coords:{latitude:loc.lat, longitude:loc.lng, accuracy:25, altitude:null, altitudeAccuracy:null, heading:null, speed:null}, timestamp:Date.now()});
                return;
              }
            } catch(x) {}
            if (err) err(e);
          }, opts);
        };
      }

      try {
        var _push = history.pushState;
        history.pushState = function(){
          _push.apply(this, arguments);
          try {
            var path = (location.pathname||'').toLowerCase();
            var session = false;
            try { session = !!localStorage.getItem('restaurant_session'); } catch(e) {}
            var onLogin = path.indexOf('login') !== -1 || path.indexOf('register') !== -1;
            if (session && !onLogin && window.ZyploApp && ZyploApp.setLoggedIn) ZyploApp.setLoggedIn(true);
          } catch(e) {}
        };
      } catch(e) {}
    }
  } catch(e) {}
})();
"""
}
