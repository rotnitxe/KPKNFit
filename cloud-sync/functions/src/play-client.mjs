import {GoogleAuth} from 'google-auth-library';
import {fail} from './protocol.mjs';

export class PlayClient {
  constructor(packageName) {
    this.packageName = packageName;
    this.auth = new GoogleAuth({scopes: ['https://www.googleapis.com/auth/androidpublisher']});
  }
  async request(path, method = 'GET', data) {
    if (process.env.FUNCTIONS_EMULATOR === 'true' || process.env.GCLOUD_PROJECT?.startsWith('demo-')) {
      fail('failed-precondition', 'Live Play requests are disabled in local emulators');
    }
    try {
      const client = await this.auth.getClient();
      return (await client.request({url: `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(this.packageName)}/${path}`,
        method, ...(data ? {data} : {})})).data;
    } catch (error) {
      // SDK error objects can contain request URLs and purchase tokens. They never leave this boundary.
      fail(error?.response?.status === 404 ? 'not-found' : 'unavailable', 'Google Play validation is unavailable');
    }
  }
  get(token) { return this.request(`purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`); }
  acknowledge(token, product) { return this.request(`purchases/subscriptions/${encodeURIComponent(product)}/tokens/${encodeURIComponent(token)}:acknowledge`, 'POST', {}); }
  cancel(token) { return this.request(`purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}:cancel`, 'POST',
    {cancellationContext: {cancellationType: 'USER_REQUESTED_STOP_RENEWALS'}}); }
}
