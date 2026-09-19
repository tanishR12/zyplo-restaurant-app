package com.zyplo.restaurant.bridge

object OrderDetectorJs {
    const val SCRIPT = """
(function(){
  if (window.__zyploNativeHooked) return;
  window.__zyploNativeHooked = true;
  function send(payload){
    try { ZyploApp.onNewOrder(typeof payload === 'string' ? payload : JSON.stringify(payload)); } catch(e) {}
  }
  function markLoggedIn(){
    try {
      var path = (location.pathname||'').toLowerCase();
      var session = false;
      try { session = !!localStorage.getItem('restaurant_session'); } catch(e) {}
      var logged = session || (path.indexOf('restaurant') !== -1 && path.indexOf('login') === -1 && path.indexOf('register') === -1);
      ZyploApp.setLoggedIn(!!logged);
    } catch(e) {}
  }
  markLoggedIn();
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = async function(){
      var res = await origFetch.apply(this, arguments);
      try {
        var url = '';
        if (typeof arguments[0] === 'string') url = arguments[0];
        else if (arguments[0] && arguments[0].url) url = arguments[0].url;
            if (/order/i.test(url)) {
          var clone = res.clone();
          clone.text().then(function(t){
            if (/pending|incoming|new[_ ]order|status":"new|awaiting|placed|confirmed/i.test(t)) {
              send(t.length > 4000 ? {source:'fetch', url:url, body:t.slice(0,4000)} : (function(){ try { return JSON.parse(t); } catch(e){ return {source:'fetch', url:url, body:t}; } })());
            }
          }).catch(function(){});
        }
      } catch(e) {}
      return res;
    };
  }
  var lastHash = '';
  function scan(){
    try {
      var text = (document.body && document.body.innerText) || '';
      if (/new order|incoming order|pending order|accept order/i.test(text)) {
        var hash = text.slice(0, 400);
        if (hash !== lastHash) {
          lastHash = hash;
          send({source:'dom'});
        }
      }
    } catch(e) {}
  }
  setInterval(scan, 4000);
  if (document.body) {
    new MutationObserver(scan).observe(document.body, {childList:true, subtree:true});
  }
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
