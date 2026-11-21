# ZAP Scanning Report

ZAP by [Checkmarx](https://checkmarx.com/).


## Summary of Alerts

| Risk Level | Number of Alerts |
| --- | --- |
| High | 0 |
| Medium | 2 |
| Low | 2 |
| Informational | 3 |




## Alerts

| Name | Risk Level | Number of Instances |
| --- | --- | --- |
| CSP: style-src unsafe-inline | Medium | 10 |
| ZAP is Out of Date | Medium | 1 |
| Dangerous JS Functions | Low | 1 |
| Timestamp Disclosure - Unix | Low | 16 |
| Information Disclosure - Suspicious Comments | Informational | 11 |
| Storable and Cacheable Content | Informational | 1 |
| Storable but Non-Cacheable Content | Informational | 11 |




## Alert Detail



### [ CSP: style-src unsafe-inline ](https://www.zaproxy.org/docs/alerts/10055/)



##### Medium (High)

### Description

Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks. Including (but not limited to) Cross Site Scripting (XSS), and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved sources of content that browsers should be allowed to load on that page — covered types are JavaScript, CSS, HTML frames, fonts, images and embeddable objects such as Java applets, ActiveX, audio and video files.

* URL: http://web:8080
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/admin
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/admin/
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/circles
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/questionnaire
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/robots.txt
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/scan
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/sitemap.xml
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`
* URL: http://web:8080/visitor
  * Method: `GET`
  * Parameter: `Content-Security-Policy`
  * Attack: ``
  * Evidence: `default-src 'self'; script-src 'self' 'sha256-67fhrP0+BkBqmgGGXTtgiVO/9EQs3QruYNU/7fnRkI8='; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  * Other Info: `style-src includes unsafe-inline.`

Instances: 10

### Solution

Ensure that your web server, application server, load balancer, etc. is properly configured to set the Content-Security-Policy header.

### Reference


* [ https://www.w3.org/TR/CSP/ ](https://www.w3.org/TR/CSP/)
* [ https://caniuse.com/#search=content+security+policy ](https://caniuse.com/#search=content+security+policy)
* [ https://content-security-policy.com/ ](https://content-security-policy.com/)
* [ https://github.com/HtmlUnit/htmlunit-csp ](https://github.com/HtmlUnit/htmlunit-csp)
* [ https://developers.google.com/web/fundamentals/security/csp#policy_applies_to_a_wide_variety_of_resources ](https://developers.google.com/web/fundamentals/security/csp#policy_applies_to_a_wide_variety_of_resources)


#### CWE Id: [ 693 ](https://cwe.mitre.org/data/definitions/693.html)


#### WASC Id: 15

#### Source ID: 3

### [ ZAP is Out of Date ](https://www.zaproxy.org/docs/alerts/10116/)



##### Medium (High)

### Description

The version of ZAP you are using to test your app is out of date and is no longer being updated.
The risk level is set based on how out of date your ZAP version is.

* URL: http://web:8080/favicon.ico
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: ``
  * Other Info: `The latest version of ZAP is 2.17.0`

Instances: 1

### Solution

Download the latest version of ZAP from https://www.zaproxy.org/download/ and install it.

### Reference


* [ https://www.zaproxy.org/download/ ](https://www.zaproxy.org/download/)


#### CWE Id: [ 1104 ](https://cwe.mitre.org/data/definitions/1104.html)


#### WASC Id: 45

#### Source ID: 3

### [ Dangerous JS Functions ](https://www.zaproxy.org/docs/alerts/10110/)



##### Low (Low)

### Description

A dangerous JS function seems to be in use that would leave the site vulnerable.

* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `eval(`
  * Other Info: ``

Instances: 1

### Solution

See the references for security advice on the use of these functions.

### Reference


* [ https://angular.io/guide/security ](https://angular.io/guide/security)


#### CWE Id: [ 749 ](https://cwe.mitre.org/data/definitions/749.html)


#### Source ID: 3

### [ Timestamp Disclosure - Unix ](https://www.zaproxy.org/docs/alerts/10096/)



##### Low (Low)

### Description

A timestamp was disclosed by the application/web server. - Unix

* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1518500249`
  * Other Info: `1518500249, which evaluates to: 2018-02-13 05:37:29.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1540483477`
  * Other Info: `1540483477, which evaluates to: 2018-10-25 16:04:37.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1604231423`
  * Other Info: `1604231423, which evaluates to: 2020-11-01 11:50:23.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1687547391`
  * Other Info: `1687547391, which evaluates to: 2023-06-23 19:09:51.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1714657791`
  * Other Info: `1714657791, which evaluates to: 2024-05-02 13:49:51.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1724754687`
  * Other Info: `1724754687, which evaluates to: 2024-08-27 10:31:27.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1732584193`
  * Other Info: `1732584193, which evaluates to: 2024-11-26 01:23:13.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1768516095`
  * Other Info: `1768516095, which evaluates to: 2026-01-15 22:28:15.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1784335871`
  * Other Info: `1784335871, which evaluates to: 2026-07-18 00:51:11.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1804477439`
  * Other Info: `1804477439, which evaluates to: 2027-03-08 03:43:59.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1859775393`
  * Other Info: `1859775393, which evaluates to: 2028-12-07 04:16:33.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `1887473919`
  * Other Info: `1887473919, which evaluates to: 2029-10-23 18:18:39.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `2005441023`
  * Other Info: `2005441023, which evaluates to: 2033-07-20 02:57:03.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `2070474495`
  * Other Info: `2070474495, which evaluates to: 2035-08-11 19:48:15.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `2096890111`
  * Other Info: `2096890111, which evaluates to: 2036-06-12 13:28:31.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `2104542553`
  * Other Info: `2104542553, which evaluates to: 2036-09-09 03:09:13.`

Instances: 16

### Solution

Manually confirm that the timestamp data is not sensitive, and that the data cannot be aggregated to disclose exploitable patterns.

### Reference


* [ https://cwe.mitre.org/data/definitions/200.html ](https://cwe.mitre.org/data/definitions/200.html)


#### CWE Id: [ 200 ](https://cwe.mitre.org/data/definitions/200.html)


#### WASC Id: 13

#### Source ID: 3

### [ Information Disclosure - Suspicious Comments ](https://www.zaproxy.org/docs/alerts/10027/)



##### Informational (Low)

### Description

The response appears to contain suspicious comments which may help an attacker. Note: Matches made within script blocks or files are against the entire content not only comments.

* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `admin`
  * Other Info: `The following pattern was used: \bADMIN\b and was detected 9 times, the first in the element starting with: "__d(function(g,r,i,a,m,e,d){const t=Object.defineProperties({},{"./(tabs)/_layout.tsx":{enumerable:!0,get:()=>r(d[0])},"./(tabs)", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `bug`
  * Other Info: `The following pattern was used: \bBUG\b and was detected 10 times, the first in the element starting with: "__d(function(g,r,i,a,m,e,d){"use strict";var t=r(d[0]);Object.defineProperty(e,"__esModule",{value:!0}),e.sortRoutes=e.sortRoute", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `bugs`
  * Other Info: `The following pattern was used: \bBUGS\b and was detected in the element starting with: "__d(function(e,a,r,t,n,i,s){n.exports={name:"react-native-worklets",version:"0.7.4",description:"The React Native multithreading", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `debug`
  * Other Info: `The following pattern was used: \bDEBUG\b and was detected 9 times, the first in the element starting with: "__d(function(g,r,i,a,m,_e,_d){"use strict";Object.defineProperty(_e,'__esModule',{value:!0}),_e.updateDevicePushTokenAsync=async", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `from`
  * Other Info: `The following pattern was used: \bFROM\b and was detected 56 times, the first in the element starting with: "__d(function(g,r,i,a,m,e,d){"use strict";Object.defineProperty(e,"__esModule",{value:!0}),e.transformKeys=e.inlineSpecificity=e.", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `later`
  * Other Info: `The following pattern was used: \bLATER\b and was detected in the element starting with: "__d(function(g,r,i,a,m,_e,_d){"use strict";function e(e){return e&&e.__esModule?e:{default:e}}Object.defineProperty(_e,'__esModu", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `query`
  * Other Info: `The following pattern was used: \bQUERY\b and was detected 3 times, the first in the element starting with: "__d(function(g,r,i,_a,m,e,d){'use strict';const t=r(d[0]),n=r(d[1]),a=r(d[2]),o=r(d[3]),s=Symbol('encodeFragmentIdentifier');fun", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `select`
  * Other Info: `The following pattern was used: \bSELECT\b and was detected 18 times, the first in the element starting with: "__d(function(g,r,i,a,m,_e,d){"use strict";function e(e){return e&&e.__esModule?e:{default:e}}Object.defineProperty(_e,'__esModul", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `User`
  * Other Info: `The following pattern was used: \bUSER\b and was detected 11 times, the first in the element starting with: "const u=(0,((e=t)&&e.__esModule?e:{default:e}).default)("User",[["path",{d:"M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",key:"975k", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `username`
  * Other Info: `The following pattern was used: \bUSERNAME\b and was detected in the element starting with: "__d(function(g,r,i,a,m,_e,d){"use strict";function e(e){return e&&e.__esModule?e:{default:e}}Object.defineProperty(_e,'__esModul", see evidence field for the suspicious comment/snippet.`
* URL: http://web:8080/_expo/static/js/web/entry-fff2a5e231ae2d0de4dcda73e0f5d03f.js
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `where`
  * Other Info: `The following pattern was used: \bWHERE\b and was detected 2 times, the first in the element starting with: "__d(function(g,r,_i,_a,m,e,d){'use strict';Object.defineProperty(e,'__esModule',{value:!0}),e.buildWorkletsHash=i,e.buildDepende", see evidence field for the suspicious comment/snippet.`

Instances: 11

### Solution

Remove all comments that return information that may help an attacker and fix any underlying problems they refer to.

### Reference



#### CWE Id: [ 200 ](https://cwe.mitre.org/data/definitions/200.html)


#### WASC Id: 13

#### Source ID: 3

### [ Storable and Cacheable Content ](https://www.zaproxy.org/docs/alerts/10049/)



##### Informational (Medium)

### Description

The response contents are storable by caching components such as proxy servers, and may be retrieved directly from the cache, rather than from the origin server by the caching servers, in response to similar requests from other users. If the response data is sensitive, personal or user-specific, this may result in sensitive information being leaked. In some cases, this may even result in a user gaining complete control of the session of another user, depending on the configuration of the caching components in use in their environment. This is primarily an issue where "shared" caching servers such as "proxy" caches are configured on the local network. This configuration is typically found in corporate or educational environments, for instance.

* URL: http://web:8080/_expo/static/css/web-04b7d3ad02783e4b08ae3e07e4698d4f.css
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `max-age=31536000`
  * Other Info: ``

Instances: 1

### Solution

Validate that the response does not contain sensitive, personal or user-specific information. If it does, consider the use of the following HTTP response headers, to limit, or prevent the content being stored and retrieved from the cache by another user:
Cache-Control: no-cache, no-store, must-revalidate, private
Pragma: no-cache
Expires: 0
This configuration directs both HTTP 1.0 and HTTP 1.1 compliant caching servers to not store the response, and to not retrieve the response (without validation) from the cache, in response to a similar request.

### Reference


* [ https://datatracker.ietf.org/doc/html/rfc7234 ](https://datatracker.ietf.org/doc/html/rfc7234)
* [ https://datatracker.ietf.org/doc/html/rfc7231 ](https://datatracker.ietf.org/doc/html/rfc7231)
* [ https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html ](https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html)


#### CWE Id: [ 524 ](https://cwe.mitre.org/data/definitions/524.html)


#### WASC Id: 13

#### Source ID: 3

### [ Storable but Non-Cacheable Content ](https://www.zaproxy.org/docs/alerts/10049/)



##### Informational (Medium)

### Description

The response contents are storable by caching components such as proxy servers, but will not be retrieved directly from the cache, without validating the request upstream, in response to similar requests from other users.

* URL: http://web:8080
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/admin/
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/assets/assets/fonts/SpaceMono-Regular.49a79d66bdea2debf1832bf4d7aca127.ttf
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/circles
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/favicon.ico
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/questionnaire
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/robots.txt
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/scan
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/sitemap.xml
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``
* URL: http://web:8080/visitor
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-cache`
  * Other Info: ``

Instances: 11

### Solution



### Reference


* [ https://datatracker.ietf.org/doc/html/rfc7234 ](https://datatracker.ietf.org/doc/html/rfc7234)
* [ https://datatracker.ietf.org/doc/html/rfc7231 ](https://datatracker.ietf.org/doc/html/rfc7231)
* [ https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html ](https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html)


#### CWE Id: [ 524 ](https://cwe.mitre.org/data/definitions/524.html)


#### WASC Id: 13

#### Source ID: 3


