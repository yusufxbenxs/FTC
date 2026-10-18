// Shared automation block definitions. Loaded by the content script, the
// panel page and the background worker, so it must stay dependency-free.
//
// ctx says where a block can run:
//   'page' - inside a YouTube tab (player control, reading the video page)
//   'bg'   - in the background worker (schedules, YouTube Data API)
//   'any'  - either place
//
// Safety policy: there are deliberately NO blocks that like, dislike,
// subscribe, comment, watch-to-farm-views, or otherwise fake engagement.
// Automations may only control the user's own player, remind the user, or
// act on the user's OWN channel through the official YouTube Data API.
(function () {
  const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  const TRIGGERS = {
    video_open:   { label: 'When I open a video',       ctx: 'page', icon: 'play',  fields: [] },
    youtube_open: { label: 'When I open YouTube',        ctx: 'page', icon: 'yt',    fields: [] },
    video_end:    { label: 'When a video ends',          ctx: 'page', icon: 'stop',  fields: [] },
    manual:       { label: 'When I tap Run',             ctx: 'bg',   icon: 'tap',   fields: [] },
    daily:        { label: 'Every day at',               ctx: 'bg',   icon: 'clock', fields: [
      { k: 'time', type: 'time', def: '09:00' }] },
    weekly:       { label: 'Every week on',              ctx: 'bg',   icon: 'cal',   fields: [
      { k: 'day', type: 'select', options: DAYS, def: 'Friday' },
      { k: 'time', type: 'time', def: '17:00', prefix: 'at' }] },
    on_date:      { label: 'On a date',                  ctx: 'bg',   icon: 'cal',   fields: [
      { k: 'datetime', type: 'datetime-local', def: '' }] },
    likes_reach:  { label: 'When my video reaches',      menu: 'Likes milestone',      ctx: 'bg', icon: 'like',  auth: true, fields: [
      { k: 'count', type: 'number', def: 100000, suffix: 'likes' },
      { k: 'videoId', type: 'text', def: '', ph: 'Video URL or ID', prefix: 'video' }] },
    views_reach:  { label: 'When my video reaches',      menu: 'Views milestone',      ctx: 'bg', icon: 'eye',   auth: true, fields: [
      { k: 'count', type: 'number', def: 1000000, suffix: 'views' },
      { k: 'videoId', type: 'text', def: '', ph: 'Video URL or ID', prefix: 'video' }] },
    subs_reach:   { label: 'When my channel reaches',    menu: 'Subscriber milestone', ctx: 'bg', icon: 'users', auth: true, fields: [
      { k: 'count', type: 'number', def: 10000, suffix: 'subscribers' }] }
  };

  const CONDITIONS = {
    channel_has:  { label: 'Channel name contains', ctx: 'page', fields: [{ k: 'text', type: 'text', def: '', ph: 'e.g. Lectures' }] },
    title_has:    { label: 'Video title contains',  ctx: 'page', fields: [{ k: 'text', type: 'text', def: '', ph: 'e.g. podcast' }] },
    time_between: { label: 'Time is between',       ctx: 'any',  fields: [
      { k: 'from', type: 'time', def: '23:00' }, { k: 'to', type: 'time', def: '06:00', prefix: 'and' }] },
    weekday_is:   { label: 'Day is',                ctx: 'any',  fields: [{ k: 'day', type: 'select', options: DAYS, def: 'Saturday' }] }
  };

  const CATEGORIES = {
    player:  { label: 'Player',  color: '#3ea6ff' },
    notify:  { label: 'Notify',  color: '#ff9f1c' },
    creator: { label: 'Creator', color: '#ff4e45' },
    flow:    { label: 'Flow',    color: '#8d8d8d' }
  };

  const ACTIONS = {
    set_speed:  { label: 'Set playback speed', cat: 'player', ctx: 'page', fields: [
      { k: 'speed', type: 'select', options: ['0.5', '0.75', '1', '1.25', '1.5', '1.75', '2'], def: '1.5', suffix: '×' }] },
    set_volume: { label: 'Set volume',         cat: 'player', ctx: 'page', fields: [
      { k: 'volume', type: 'number', def: 50, min: 0, max: 100, suffix: '%' }] },
    mute:       { label: 'Mute',               cat: 'player', ctx: 'page', fields: [] },
    pause:      { label: 'Pause video',        cat: 'player', ctx: 'page', fields: [] },
    theater:    { label: 'Turn on theater mode', cat: 'player', ctx: 'page', fields: [] },
    skip_to:    { label: 'Skip to',            cat: 'player', ctx: 'page', fields: [
      { k: 'seconds', type: 'number', def: 30, min: 0, suffix: 'seconds' }] },
    loop:       { label: 'Loop this video',    cat: 'player', ctx: 'page', fields: [] },
    captions:   { label: 'Turn captions on',   cat: 'player', ctx: 'page', fields: [] },

    notify:     { label: 'Show notification',  cat: 'notify', ctx: 'any', fields: [
      { k: 'title', type: 'text', def: 'YouTube', ph: 'Title' },
      { k: 'message', type: 'text', def: '', ph: 'Message' }] },
    open_url:   { label: 'Open page',          cat: 'notify', ctx: 'any', fields: [
      { k: 'url', type: 'text', def: 'https://studio.youtube.com/', ph: 'https://…' }] },
    remind_post: { label: 'Remind me to post', cat: 'notify', ctx: 'any', fields: [
      { k: 'text', type: 'textarea', def: '', ph: 'Community post text (copied for you to paste)' }],
      note: 'YouTube has no public API for community posts, so this reminds you and opens Studio with your text ready to copy.' },

    publish_video: { label: 'Upload & publish video', cat: 'creator', ctx: 'bg', auth: true, fields: [
      { k: 'file', type: 'file', def: null, accept: 'video/*' },
      { k: 'title', type: 'text', def: '', ph: 'Title' },
      { k: 'description', type: 'textarea', def: '', ph: 'Description' },
      { k: 'tags', type: 'text', def: '', ph: 'tags, comma, separated' },
      { k: 'privacy', type: 'select', options: ['public', 'unlisted', 'private'], def: 'public', prefix: 'as' },
      { k: 'publishAt', type: 'datetime-local', def: '', prefix: 'go public at (optional)' }],
      note: 'Uses the official YouTube Data API on your own channel. The video file is stored in this browser until it is uploaded.' },

    wait:       { label: 'Wait',               cat: 'flow', ctx: 'any', fields: [
      { k: 'seconds', type: 'number', def: 5, min: 0, max: 25, suffix: 'seconds' }] }
  };

  // Templates shown in the automation gallery.
  const TEMPLATES = [
    { name: 'Thank-you video at 100K likes', desc: 'Publishes a celebration video the moment your video hits 100K likes.',
      trigger: { type: 'likes_reach', params: { count: 100000, videoId: '' } }, conditions: [],
      actions: [{ type: 'publish_video', params: { title: 'Ty For 100K Likes!!!', description: 'Thank you all so much for 100,000 likes ❤️', tags: '100k, thank you', privacy: 'public', publishAt: '' } }] },
    { name: 'Upload now, go public later', desc: 'Uploads privately and lets YouTube flip it public on your date.',
      trigger: { type: 'manual', params: {} }, conditions: [],
      actions: [{ type: 'publish_video', params: { title: '', description: '', tags: '', privacy: 'private', publishAt: '' } }] },
    { name: 'Friday post reminder', desc: 'Every Friday at 17:00, reminds you to post with your text ready.',
      trigger: { type: 'weekly', params: { day: 'Friday', time: '17:00' } }, conditions: [],
      actions: [{ type: 'remind_post', params: { text: 'New video drops this weekend! 🎬' } }] },
    { name: 'Lecture mode', desc: 'Lecture channels play at 1.5× in theater mode.',
      trigger: { type: 'video_open', params: {} }, conditions: [{ type: 'channel_has', params: { text: 'Lecture' } }],
      actions: [{ type: 'set_speed', params: { speed: '1.5' } }, { type: 'theater', params: {} }] },
    { name: 'Bedtime', desc: 'After 23:00, pauses new videos and nudges you to sleep.',
      trigger: { type: 'video_open', params: {} }, conditions: [{ type: 'time_between', params: { from: '23:00', to: '06:00' } }],
      actions: [{ type: 'pause', params: {} }, { type: 'notify', params: { title: 'It\'s late', message: 'Maybe save this one for tomorrow?' } }] },
    { name: 'Always theater', desc: 'Every video opens in theater mode with captions.',
      trigger: { type: 'video_open', params: {} }, conditions: [],
      actions: [{ type: 'theater', params: {} }, { type: 'captions', params: {} }] },
    { name: 'Daily Studio check', desc: 'Opens YouTube Studio every morning at 9.',
      trigger: { type: 'daily', params: { time: '09:00' } }, conditions: [],
      actions: [{ type: 'open_url', params: { url: 'https://studio.youtube.com/' } }] }
  ];

  function defaults(fields) {
    const p = {};
    for (const f of fields || []) p[f.k] = f.def;
    return p;
  }

  function minutesOf(hhmm) {
    const [h, m] = String(hhmm || '0:0').split(':').map(Number);
    return (h || 0) * 60 + (m || 0);
  }

  function checkCondition(cond, info) {
    const p = cond.params || {};
    const now = new Date();
    switch (cond.type) {
      case 'channel_has': return !!info && (info.channel || '').toLowerCase().includes(String(p.text || '').toLowerCase());
      case 'title_has':   return !!info && (info.title || '').toLowerCase().includes(String(p.text || '').toLowerCase());
      case 'time_between': {
        const t = now.getHours() * 60 + now.getMinutes();
        const a = minutesOf(p.from), b = minutesOf(p.to);
        return a <= b ? (t >= a && t < b) : (t >= a || t < b); // range can wrap midnight
      }
      case 'weekday_is': return DAYS[now.getDay()] === p.day;
      default: return false;
    }
  }

  // Next time a schedule trigger should fire, in ms since epoch (or null).
  function nextFireTime(trigger, from = Date.now()) {
    const p = trigger.params || {};
    const base = new Date(from);
    if (trigger.type === 'daily' || trigger.type === 'weekly') {
      const [h, m] = String(p.time || '09:00').split(':').map(Number);
      const d = new Date(base);
      d.setHours(h, m, 0, 0);
      if (trigger.type === 'weekly') {
        const want = DAYS.indexOf(p.day);
        let add = (want - d.getDay() + 7) % 7;
        if (add === 0 && d.getTime() <= from) add = 7;
        d.setDate(d.getDate() + add);
      } else if (d.getTime() <= from) {
        d.setDate(d.getDate() + 1);
      }
      return d.getTime();
    }
    if (trigger.type === 'on_date') {
      const t = new Date(p.datetime).getTime();
      return isFinite(t) && t > from ? t : null;
    }
    return null;
  }

  function parseVideoId(input) {
    const s = String(input || '').trim();
    const m = s.match(/(?:v=|youtu\.be\/|shorts\/|live\/)([\w-]{11})/);
    return m ? m[1] : (/^[\w-]{11}$/.test(s) ? s : '');
  }

  // The official community catalog (a public GitHub repo with catalog.json on main).
  // Users can point FireTube at a different one in Settings.
  globalThis.FT_CONFIG = { catalogRepo: 'yusufxbenxs/FTC', catalogBranch: 'main' };

  globalThis.FT_BLOCKS = { DAYS, TRIGGERS, CONDITIONS, ACTIONS, CATEGORIES, TEMPLATES, defaults, checkCondition, nextFireTime, parseVideoId };
})();
