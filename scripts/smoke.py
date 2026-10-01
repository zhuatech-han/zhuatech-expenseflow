#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Exercise isolated MySQL deployment through public HTTP APIs. Never print credentials."""
import argparse, base64, json, urllib.request, urllib.error, http.cookiejar, secrets, datetime, uuid, os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--url',default='http://127.0.0.1:8101');p.add_argument('--verify',action='store_true');p.add_argument('--state',default='/private/tmp/expenseflow-smoke-state.json');args=p.parse_args()
root=Path(__file__).resolve().parents[1]
config=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
class Client:
    """Cookie and CSRF protected public HTTP session. 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self,username,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=None;self.call('/auth/login','POST',{'username':username,'password':password});self.csrf=None
    def call(self,path,method='GET',body=None,expected=200,raw=False,headers=None):
        h=headers or {}
        if method!='GET':
            if not self.csrf:self.csrf=self.call('/auth/csrf')
            h[self.csrf['header']]=self.csrf['token']
        if body is not None and not isinstance(body,bytes):body=json.dumps(body).encode();h['Content-Type']='application/json'
        request=urllib.request.Request(args.url+'/api'+path,data=body,method=method,headers=h)
        try:
            response=self.opener.open(request,timeout=30);status=response.status;data=response.read()
        except urllib.error.HTTPError as e:status=e.code;data=e.read()
        assert status==expected,f'{method} {path}: expected {expected}, got {status}'
        return data if raw else json.loads(data)
    def command(self,case,action,**values):
        return self.call('/cases/'+str(case['record']['id'])+'/commands/'+action,'POST',{'version':case['record']['version'],'requestKey':str(uuid.uuid4()),'note':'验收测试：核对费用用途与凭证','allowOverBudget':False,**values})
admin=Client('admin',config['ADMIN_PASSWORD'])
assert json.load(urllib.request.urlopen(args.url+'/actuator/health'))['status']=='UP'
assert urllib.request.urlopen(args.url).status==200
if args.verify:
    state=json.loads(Path(args.state).read_text());case=admin.call('/cases/'+str(state['claimId']))
    assert case['record']['status']=='PART_PAID' and case['record']['paidCents']==3000
    assert len(case['payments'])==2 and len(case['evidence'])==1 and case['evidence'][0]['frozen']
    assert len(admin.call('/cases/'+str(state['claimId'])+'/evidence/'+str(case['evidence'][0]['id']),raw=True))>20
    assert any(e['snapshot'] for e in case['events'])
    print('PASS: restart preserved case, payments, reversal, evidence and snapshots')
else:
    stamp=uuid.uuid4().hex[:8];password='Aa9'+secrets.token_urlsafe(20)
    dept=admin.call('/admin/departments','POST',{'name':'验收测试部门 '+stamp})
    roles={r['name']:r['id'] for r in admin.call('/admin/roles')}
    accounts={}
    for role,name in [('员工','employee'),('部门主管','manager'),('财务复核','finance'),('付款登记','payer'),('员工','outsider')]:
        d=dept['id'] if name!='outsider' else 1
        accounts[name]=admin.call('/admin/users','POST',{'username':'check_'+name+'_'+stamp,'displayName':{'employee':'测试员工','manager':'测试主管','finance':'测试财务','payer':'测试付款岗','outsider':'测试外部门员工'}[name],'password':password,'roleId':roles[role],'departmentId':d,'enabled':True})
    clients={name:Client(a['username'],password) for name,a in accounts.items()}
    emp=clients['employee'];mgr=clients['manager'];fin=clients['finance'];pay=clients['payer'];other=clients['outsider']
    today=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).date().isoformat();month=today[:7]
    admin.call('/budgets','POST',{'departmentId':dept['id'],'category':'OFFICE','budgetMonth':month,'limit':'1000.00'})
    draft={'kind':'APPLICATION','title':'验收测试：办公用品费用申请','category':'OFFICE','budgetMonth':month,'amount':'120.00','purpose':'测试数据，用于验证申请转报销、审批和付款登记，不代表实际业务。','managerId':accounts['manager']['id'],'financeId':accounts['finance']['id'],'lines':[]}
    app=emp.call('/cases','POST',draft);aid=app['record']['id']
    app=emp.command(app,'submit');app=mgr.command(mgr.call('/cases/'+str(aid)),'manager-approve');app=fin.command(fin.call('/cases/'+str(aid)),'finance-approve')
    assert app['budget']['reservedCents']==12000
    draft.update(kind='CLAIM',title='验收测试：办公用品报销',applicationId=aid,lines=[{'expenseDate':today,'description':'验收测试用品费用明细','amount':'80.01','issuer':'验收测试开具方','reference':'CHECK-'+stamp}])
    claim=emp.call('/cases','POST',draft);cid=claim['record']['id']
    png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=');boundary='check'+uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="check.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+f'\r\n--{boundary}--\r\n'.encode())
    claim=emp.call(f'/cases/{cid}/evidence?version='+str(claim['record']['version']),'POST',body,headers={'Content-Type':'multipart/form-data; boundary='+boundary})
    claim=emp.command(claim,'submit');claim=mgr.command(mgr.call('/cases/'+str(cid)),'manager-approve');claim=fin.command(fin.call('/cases/'+str(cid)),'finance-approve')
    assert claim['budget']['reservedCents']==0 and claim['budget']['committedCents']==8001
    claim=pay.command(pay.call('/cases/'+str(cid)),'pay',amount='30.00',reference='CHECK-PAY-1-'+stamp,method='BANK',paymentDate=today)
    claim=pay.command(claim,'pay',amount='50.01',reference='CHECK-PAY-2-'+stamp,method='BANK',paymentDate=today)
    assert claim['record']['status']=='PAID'
    claim=pay.command(claim,'reverse',paymentId=claim['payments'][1]['id'])
    assert claim['record']['status']=='PART_PAID' and claim['record']['paidCents']==3000 and claim['budget']['committedCents']==8001
    other.call('/cases/'+str(cid),expected=403);other.call('/admin/users',expected=403)
    assert other.call('/cases')['total']==0
    other.call('/cases/'+str(cid)+'/evidence/'+str(claim['evidence'][0]['id']),expected=403)
    result=emp.call('/cases/'+str(cid)+'/report.json')
    assert 'passwordHash' not in json.dumps(result) and 'zhuatech' not in json.dumps(result)
    denied=urllib.request.Request(args.url+'/api/cases',data=b'{}',method='POST',headers={'Content-Type':'application/json'})
    try:urllib.request.urlopen(denied);raise AssertionError('Anonymous write allowed')
    except urllib.error.HTTPError as e:assert e.code in [401,403]
    state={'claimId':cid,'applicationId':aid,'accounts':accounts,'password':password,'departmentId':dept['id'],'month':month}
    fd=os.open(args.state,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as out:json.dump(state,out)
    print('PASS: admin login, application approval, linked claim, evidence, partial/full payment, reversal, budget accounting, data scope, denied access, export and CSRF')
