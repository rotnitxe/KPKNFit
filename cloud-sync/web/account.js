import {initializeApp} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-app.js';
import {getAuth, setPersistence, inMemoryPersistence, signInWithEmailAndPassword, signInWithPopup, GoogleAuthProvider,
  sendPasswordResetEmail, reauthenticateWithCredential, EmailAuthProvider, reauthenticateWithPopup, signOut} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-auth.js';
import {getFunctions, httpsCallable, connectFunctionsEmulator} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-functions.js';
import {connectAuthEmulator} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-auth.js';
import {initializeAppCheck, ReCaptchaEnterpriseProvider} from 'https://www.gstatic.com/firebasejs/13.0.0/firebase-app-check.js';

const texts = {
  es: {title:'Eliminar mi cuenta KPKN', intro:'Puedes eliminar tu cuenta aunque ya hayas desinstalado la app. Primero confirma tu identidad.', email:'Correo', password:'Contraseña', signin:'Entrar', google:'Continuar con Google', recover:'Recuperar contraseña', scope:'Se retirarán tus datos sincronizados, archivos privados y acceso a la cuenta. Se detendrán las renovaciones de las suscripciones vinculadas. Conserva antes cualquier copia que necesites.', copies:'Las copias que exportaste y los datos de un dispositivo sin conexión permanecen en ese dispositivo hasta que los borres allí. La eliminación impide que vuelva a cargar datos a esta cuenta.', confirm:'Entiendo y quiero eliminar mi cuenta y mis datos de KPKN.', delete:'Eliminar cuenta', signout:'Cerrar sesión', privacy:'Privacidad', terms:'Condiciones', busy:'Procesando…', error:'No se pudo completar la operación. Comprueba tus datos y conexión e inténtalo de nuevo.', unavailable:'La gestión de cuentas aún no está configurada para esta instalación.', reset:'Si el correo tiene una cuenta, recibirás instrucciones para recuperar el acceso.', deleted:'Tu cuenta y sus datos sincronizados se han eliminado.', pending:'Se ha registrado tu solicitud y bloqueado el acceso a la cuenta. La retirada de datos y la cancelación de renovaciones continúan; no necesitas repetir la solicitud.'},
  en: {title:'Delete my KPKN account', intro:'You can delete your account after uninstalling the app. First confirm your identity.', email:'Email', password:'Password', signin:'Sign in', google:'Continue with Google', recover:'Reset password', scope:'Your synced data, private files and account access will be removed. Renewals of linked subscriptions will be stopped. Keep any copies you need before continuing.', copies:'Exported copies and data on an offline device remain there until you erase them on that device. Deletion prevents it from uploading data to this account again.', confirm:'I understand and want to delete my KPKN account and data.', delete:'Delete account', signout:'Sign out', privacy:'Privacy', terms:'Terms', busy:'Processing…', error:'The operation could not be completed. Check your details and connection, then try again.', unavailable:'Account management is not configured for this installation yet.', reset:'If this email has an account, you will receive instructions to recover access.', deleted:'Your account and synced data have been deleted.', pending:'Your request has been recorded and account access blocked. Data removal and renewal cancellation are continuing; you do not need to repeat the request.'}
};
let language = navigator.language.startsWith('es') ? 'es' : 'en';
texts.es.cancel = 'Cancelar'; texts.en.cancel = 'Cancel';
const byId = id => document.getElementById(id);
const status = key => { byId('status').textContent = texts[language][key]; };
function translate() { document.documentElement.lang = language; document.querySelectorAll('[data-text]').forEach(element => { element.textContent = texts[language][element.dataset.text]; }); byId('language').textContent = language === 'es' ? 'English' : 'Español'; }
byId('language').onclick = () => { language = language === 'es' ? 'en' : 'es'; translate(); };
translate();
let auth, functions;
async function initialize() {
  const local = ['localhost', '127.0.0.1'].includes(location.hostname);
  let config, publicConfig;
  if (local) config = {projectId:'demo-kpkn-stability', appId:'1:123456789012:web:1234567890abcdef', apiKey:'emulator-only'};
  else {
    [config, publicConfig] = await Promise.all([fetch('/__/firebase/init.json').then(response => response.json()), fetch('/.well-known/kpkn-config.json').then(response => response.json())]);
    if (!publicConfig.appCheckSiteKey || !config.projectId || config.projectId.startsWith('demo-')) throw Error('unavailable');
  }
  const app = initializeApp(config);
  if (!local) initializeAppCheck(app, {provider:new ReCaptchaEnterpriseProvider(publicConfig.appCheckSiteKey), isTokenAutoRefreshEnabled:true});
  auth = getAuth(app); await setPersistence(auth, inMemoryPersistence); functions = getFunctions(app, 'us-central1');
  if (local) { connectAuthEmulator(auth, 'http://127.0.0.1:19099', {disableWarnings:true}); connectFunctionsEmulator(functions, '127.0.0.1', 15001); byId('google').hidden = true; }
}
async function perform(operation) {
  document.querySelectorAll('form button').forEach(button => { button.disabled = true; }); status('busy');
  try { await operation(); } catch { status('error'); }
  finally { document.querySelectorAll('form button').forEach(button => { button.disabled = false; }); }
}
function showAccount() { byId('identity').textContent = auth.currentUser?.email ?? ''; byId('signin').hidden = true; byId('account').hidden = false; byId('status').textContent = ''; byId('password').value = ''; }
byId('signin').onsubmit = event => { event.preventDefault(); perform(async () => { await signInWithEmailAndPassword(auth, byId('email').value.trim(), byId('password').value); showAccount(); }); };
byId('google').onclick = () => perform(async () => { await signInWithPopup(auth, new GoogleAuthProvider()); showAccount(); });
byId('recover').onclick = () => perform(async () => { await sendPasswordResetEmail(auth, byId('email').value.trim()); status('reset'); });
byId('delete').onsubmit = event => { event.preventDefault(); perform(async () => {
  const user = auth.currentUser;
  if (user.providerData.some(provider => provider.providerId === 'google.com')) await reauthenticateWithPopup(user, new GoogleAuthProvider());
  else {
    // Reauthenticate with the same proven identity immediately before the server request.
    byId('reauth').hidden = false; byId('account').hidden = true;
    byId('reauth').onsubmit = async retry => { retry.preventDefault(); perform(async () => {
      if (auth.currentUser?.uid !== user.uid) throw Error('identity-changed');
      await reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, byId('reauth-password').value));
      byId('reauth-password').value = ''; await removeAccount();
    }); };
    byId('reauth-password').focus(); byId('status').textContent = texts[language].intro; return;
  }
  await removeAccount();
}); };
async function removeAccount() {
  const result = (await httpsCallable(functions, 'deleteAccount')({confirmation:'DELETE_ACCOUNT'})).data;
  await signOut(auth); byId('account').hidden = true; byId('signin').hidden = true; byId('reauth').hidden = true;
  status(result.status === 'DELETED' ? 'deleted' : 'pending');
}
byId('signout').onclick = () => perform(async () => { await signOut(auth); byId('account').hidden = true; byId('signin').hidden = false; byId('status').textContent = ''; location.reload(); });
byId('cancel-reauth').onclick = () => { byId('reauth-password').value = ''; byId('reauth').hidden = true; showAccount(); };
try { await initialize(); } catch { document.querySelectorAll('form button').forEach(button => { button.disabled = true; }); status('unavailable'); }
