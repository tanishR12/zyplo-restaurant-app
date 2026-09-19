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

    function saveRestaurantFcm(){
      if (!token) return;
      var rest = null;
      try { rest = JSON.parse(localStorage.getItem('restaurant_session') || 'null'); } catch(e) {}
      if (!rest || !rest.restaurant_id) return;
      var anon = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml0ampjc2N5cXF4a2VpcGtjY2dsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTQwOTg4MDEsImV4cCI6MjA2OTY3NDgwMX0.ZUpcDDVxUiZ3uNgF0Mbb3HjqhY9yc_B2xWeUARDk4Yc';
      var body = JSON.stringify({
        restaurant_id: rest.restaurant_id,
        session_token: rest.session_token,
        email: rest.email,
        action: 'register_fcm',
        fcm_token: token,
        token: token,
        device_type: 'android-fcm',
        platform: 'android'
      });
      fetch('https://itjjcscyqqxkeipkccgl.supabase.co/functions/v1/get-restaurant-orders', {
        method:'POST',
        headers:{'Content-Type':'application/json','apikey':anon,'Authorization':'Bearer '+(rest.session_token||anon)},
        body: body
      }).catch(function(){});
      fetch('https://itjjcscyqqxkeipkccgl.supabase.co/functions/v1/restaurant-auth', {
        method:'POST',
        headers:{'Content-Type':'application/json','apikey':anon,'Authorization':'Bearer '+(rest.session_token||anon)},
        body: JSON.stringify({action:'register_fcm', restaurant_id: rest.restaurant_id, session_token: rest.session_token, fcm_token: token, device_type:'android-fcm'})
      }).catch(function(){});
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
            var logged = path.indexOf('restaurant') !== -1 && path.indexOf('login') === -1 && path.indexOf('register') === -1;
            if (logged && window.ZyploApp && ZyploApp.setLoggedIn) ZyploApp.setLoggedIn(true);
            saveRestaurantFcm();
          } catch(e) {}
        };
      } catch(e) {}
    }

    saveRestaurantFcm();
    setTimeout(saveRestaurantFcm, 2500);
  } catch(e) {}
})();
"""
}
