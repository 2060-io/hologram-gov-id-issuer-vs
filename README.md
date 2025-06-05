# Hologram Government Digital ID Issuer

![2060 logo](https://raw.githubusercontent.com/2060-io/.github/44bf28569fec0251a9367a9f6911adfa18a01a7c/profile/assets/2060_logo.svg)

**Hologram Gov ID Issuer** is an application that, alongside [VS Agent](https://github.com/2060-io/vs-agent) and other open source components developed by [2060.io](https://2060.io), conforms a **DIDcomm conversational [Verifiable Service](https://verana-labs.github.io/verifiable-trust-spec/#what-is-a-verifiable-service-vs)** which issues Digital ID Credentials based on users' real ID documents.

The issued credentials are **[AnonCreds](https://www.lfdecentralizedtrust.org/projects/anoncreds) Verifiable Credentials** that can be used to authenticate in other services in a privacy-preserving manner, using Zero Knowledge Proofs and supporting Selective Disclosure of credential attributes.

## Features

- Reads and verify users' Passports and National ID cards, as long as they are compatible with [ICAO 9303](https://www.icao.int/publications/pages/publication.aspx?docnum=9303) (most modern passports do so)
- Performs liveness detection and face matching of users against their ID document
- Issues an AnonCreds Verifiable Credential containing all basic attributes of the documents (such as names, photo, nationality, expiration date), which can be later presented (all or some of them) to any service
- Works with any DIDComm-capable agent that supports calls and eMRTD protocols, such as [Hologram Messenger](https://hologram.zone)
- And everything with open source software! No need to pay any license fees

## Service Architecture

The following diagram shows how the different components are combined and interact with users using Hologram app:

![](arch.svg)



## Try the demo

You can test a deployed demo of this service at [https://gov-id-issuer.demos.dev.2060.io] (TODO: update with production demo)

Once you have your Digital ID Credential, you can present it in services such as **[Hologram Gov ID Verifier](https://gov-id-verifier.demos.dev.2060.io)**.

### Gov ID Registry

a government-like registry service. Test URL: [https://hologram-gov-id-issuer.demos.2060.io](https://hologram-gov-id-issuer.demos.2060.io)

#### Scan the QR code

> TODO

![Hologram Gov ID Issuer](https://hologram-gov-id-issuer.demos.2060.io/qr?size=300&bcolor=FFFFFF&balpha=1&fcolor=000000&falpha=1)


#### Accept the Invitation
<kbd>
<img src="assets/IMG_7719.PNG" alt="invitation" border:"1" style="height:500px; border: 1px solid #EEEEEE;"/>
<img src="assets/IMG_7720.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
</kbd>

#### Create the Identity

Go to contextual menu and select "Create an Identity"

<kbd>
<img src="assets/IMG_7721.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
<img src="assets/IMG_7722.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
</kbd>

#### Capture your face

<kbd>
<img src="assets/IMG_7723.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
<img src="assets/IMG_7724.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
</kbd>

#### Verify your face

Now you verify your face, just to be sure capture was OK.

<kbd>
<img src="assets/IMG_7725.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
<img src="assets/IMG_7726.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
</kbd>

#### Receive your ID Card

<kbd>
<img src="assets/IMG_7727.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
<img src="assets/IMG_7728.PNG" alt="invitation" style="height:500px; border: 1px solid #EEEEEE;"/>
</kbd>

## Do you want to run it on your own?

This repo provides Helm charts