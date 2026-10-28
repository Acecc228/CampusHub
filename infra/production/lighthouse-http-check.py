"""Real HTTPS/Session/CSRF acceptance on loopback only; no WeChat impersonation."""
import datetime,http.cookiejar,json,pathlib,ssl,sys,time,urllib.error,urllib.parse,urllib.request
if '--apply' not in sys.argv:print('PREVIEW: create/edit/publish labelled acceptance activity through private HTTPS');sys.exit(0)
accounts=json.loads(pathlib.Path('/etc/campushub/acceptance-accounts.json').read_text())
context=ssl.create_default_context(cafile='/etc/campushub/tls/ca.crt')
class Client:
    def __init__(self):
        self.jar=http.cookiejar.CookieJar();self.opener=urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),urllib.request.HTTPCookieProcessor(self.jar))
    def request(self,path,method='GET',data=None,form=False,csrf=True):
        headers={}
        if method!='GET' and csrf:
            _,token,_=self.request('/api/v1/auth/csrf');headers[token['headerName']]=token['token']
        payload=None
        if data is not None:
            payload=(urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
            headers['Content-Type']='application/x-www-form-urlencoded' if form else 'application/json'
        try:r=self.opener.open(urllib.request.Request('https://localhost:8443'+path,data=payload,headers=headers,method=method),timeout=15)
        except urllib.error.HTTPError as error:r=error
        body=r.read();parsed=json.loads(body) if body and r.headers.get_content_type()=='application/json' else None
        return r.status,parsed,r.headers
    def login(self,role):
        account=accounts[role]
        return self.request('/api/v1/auth/login','POST',{'username':account['login'],'password':account['password']},True)
checks={}
anonymous=Client();assert anonymous.request('/api/v1/manage/activities')[0]==401;checks['anonymous401']='PASS'
admin=Client();status,identity,headers=admin.login('ADMIN');assert status==200
cookie=headers.get('Set-Cookie','');assert all(v in cookie.lower() for v in ['secure','httponly','samesite=strict'])
assert admin.request('/api/v1/auth/me')[0]==200;checks['adminSessionSecureCookie']='PASS'
assert admin.request('/api/v1/student/access')[0]==403;checks['role403']='PASS'
assert admin.request('/api/v1/manage/activities','POST',{},csrf=False)[0]==403;checks['csrf403']='PASS'
assert admin.request('/api/v1/auth/wechat-login','POST',{'code':'not-a-real-code'})[0]==503;checks['wechatDisabled503']='PASS'
# A failed WeChat login intentionally invalidates the old session.
assert admin.login('ADMIN')[0]==200
categories=admin.request('/api/v1/activity-categories')[1]
now=datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
def at(hours):return (now+datetime.timedelta(hours=hours)).isoformat().replace('+00:00','Z')
data=dict(title='Restricted cloud acceptance '+str(int(time.time())),summary='Internal deployment verification',content='Real HTTPS/MySQL; not a public campus event.',categoryId=categories[0]['id'],location='Internal acceptance only',capacity=2,registrationStart=at(-1),registrationEnd=at(24),startTime=at(48),endTime=at(50))
status,activity,_=admin.request('/api/v1/manage/activities','POST',data);assert status==201
activity_id=activity['id'];data['title']+=' edited';data['version']=activity['version']
status,activity,_=admin.request('/api/v1/manage/activities/'+str(activity_id),'PUT',data);assert status==200
status,activity,_=admin.request('/api/v1/manage/activities/'+str(activity_id)+'/publish','POST',{'version':activity['version']});assert status==200 and activity['status']=='PUBLISHED'
checks['createEditPublish']='PASS'
organizer=Client();assert organizer.login('ORGANIZER')[0]==200
assert organizer.request('/api/v1/manage/activities/'+str(activity_id))[0]==403;checks['ownership403']='PASS'
assert Client().login('STUDENT')[0]==401;checks['studentPasswordLoginDenied']='PASS'
assert admin.request('/api/v1/auth/logout','POST')[0]==204
assert admin.request('/api/v1/auth/me')[0]==401;checks['logout401']='PASS'
checks.update(activityId=activity_id,wechatOfficialLogin='BLOCKED',studentHttpLoginAndRegistration='BLOCKED')
pathlib.Path('/var/log/campushub-deploy/http-acceptance.json').write_text(json.dumps(checks,indent=2))
print(json.dumps(checks))
