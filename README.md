# UnicID Issuer DTS

![2060 logo](https://raw.githubusercontent.com/2060-io/.github/44bf28569fec0251a9367a9f6911adfa18a01a7c/profile/assets/2060_logo.svg)

## Purpose of the service

A DIDcomm conversational services (chatbot) to issue UnicID Verifiable Credentials to citizens by verifying their face against their NFC-compatible ID card.

## Device lost, app uninstalled?

If you lose you cellphone or delete the App, then can restore your Identity by simply re-connecting to the same Registry service, verifying your face, and recover your Verifiable Credential.

## Service Architecture

![](arch.svg)


## Call coordination flow

![](call-coordination.svg)

### Unicid Identity Registry

a government-like registry service. Test URL: [https://gaiaid.io](https://gaiaid.io)

#### Scan the QR code

![GaiaID](https://gaia.demos.2060.io/qr?size=300&bcolor=FFFFFF&balpha=1&fcolor=000000&falpha=1)

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

### AvatarID Registry

create your Avatar and protect it with your face biometrics. Test URL: [https://avatar.demos.2060.io/qr](https://avatar.demos.2060.io/qr)

Service is similar to the GaiaID one.

![AvatarID](https://avatar.demos.2060.io/qr?size=300&bcolor=FFFFFF&balpha=1&fcolor=000000&falpha=1)

## Deploy your own demo for your country

Go to the [kubernetes-howto](kubernetes/README.md) section.

## Setting up a development environment

Please refer to [these instructions](docker-dev/README.md).
