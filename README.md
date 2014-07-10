# AndroidNetworkManager

Basic NetworkManager implementation for Android that I use in my projects.

## Idea
The idea behind this project was to implement a generic NetworkManager for Android.
(please note that this project is long outdated and is here only as a code sample. I've switched to Retrofit library quite some time ago)


Every Android apps developer has to work with REST APIs and server-side.

And when you have to call some set of URL for a particular server, the first idea that comes to mind is to implement a simple HTTP request/response system using ApacheHttpClient or HttpURLConnection.

This particular manager started the same way, although right from the start I have tried to make a solid architecture that will allow in the future to play well on 3G networks, have ability to properly show Loading dialogs and process responses correctly.

## Architecture

On the top level of abstractions you have:
* CachedNetworkManager
* NetworkMessage
* NetworkResponse

**CachedNetworkManager** is basically an abstraction that allows not only to send network requests and process responses, but also enables HTTPCaching and allows you to implement some manual caching also.

**NetworkMessage** is the network request. It wraps around URL, query parameters, request method (POST|GET) and hides some low-level stuff.

**NetworkResponse** is what was returned from server. It hides low-level implementation and allows to just get the data.


This will be the main list of classes you will use.

NetworkManager can use either *ApacheHttpClient* implementation, or *HttpURLConnection*.

You can pick whatever you like, but I recomend to stick with HttpURLConnection - it's better maintained by Google in the SDK, while Apache implementation is no longer officially supported.

To choose an implementation you should set a **NETWORK_MANAGER_CORE** variable in *NetworkManager.java*

It can be either **NetworkManagerCore.HTTPURLCONNECTION** or **NetworkManagerCore.APACHE**.


So, accordingly you can see an internal implementation of **NetworkResponse** for both variants.

**NetworkResponseFactory** picks a proper implementation.

**ApacheHttpClientResponse** is the implementation for Apache.

**HttpUrlConnectionResponse** is the implementation for HttpUrlConnection.

Main 2 methods of **NetworkResponse** are:

*getStatus()* and *getResponseBody()*. Names are pretty straightforward, so no need to describe the details.


A few listeners are available.

**OnNetworkSendListener** is only used internaly. It's purpose is to fire messages from inner network thread. So plase don't use this listener in your project.

The listener you want to use is **NetworkListener**.

Your Activity or Fragment that exposes network will have to do 2 things.

1. implement NetworkListener

2. subscribe to NetworkManager.

I'll go into more details later in usage examples.


NetworkListener fires following messages:
* queueStart()
* queueFinish()
* queueFailed()
* requestStart(NetworkMessage message)
* requestSuccess(NetworkMessage message, NetworkResponse response)
* requestFail(NetworkMessage message, NetworkResponse response)
* requestProgress(NetworkMessage message)

These callbacks are pretty straightforward.

Please refer to javadoc documentation in the sources for more details.


And, finally, the core - **NetworkManager** class.

A few settings allow to tune NetworkManager:

**NETWORK_TIMEOUT** *(default 5000)* is number of seconds before request is considered timed out.

**MAX_FAILURES** *(default 3)* - number of failures allowed. This should be worked together with deleteOnFailure parameter of NetworkMessage.

If **deleterOnFailure** is *TRUE*, then when we fail network manager just deletes a message from queue, but

if it's *FALSE*, then we put the message to the end of the queue and try to send it once again - this is when **MAX_FAILURES** comes into play.

Also, when **MAX_FAILURES** is reached *queueFinish()* callback is fired.


**NETWORK_MANAGER_CORE** - allows to pick a low-level implementation. 

Can be either **NetworkManagerCore.APACHE** or **NetworkManagerCore.HTTPURLCONNECTION**.


So, the general idea of **NetworkManager**.

- Internal queue stores **NetworkMessages**.

- One by one manager attempts to send these messages - an internal AsyncTask is started for each of the messages.
At this point queueStart is fired.

- Depedning on the picked implementation, NetworkManager prepares a network connection and sends a message, awaiting for response.
At this point requestStart and requestProgress callbacks are fired.

- When manager gets a response from server NetworkResponse is constructed.

- If response status is 200 OK - requestSuccess is fired.
if not - requestFail is fired.

And that's all.


NetworkManager basically just hides the low-level implementation of network logic.

You implement NetworkListener in your class, subscribe to NetworkManager events, put a few messages, call releaseQueue() method and wait for results to come.


Now lets go through some actual code examples.

## Usage examples

Basic usage should may look like:

	public class MyFragment extends Fragment implements NetworkListener {

		private NetworkMessage myTestNetworkMessage;
		private ProgressDialog progressDialog;

		@Override
		public void onStart() {
			//we subscribe on events when we start
			NetworkManager networkManager = NetworkManager.getInstance();
			networkManager.subscribe(this)
		}

		public void onResume() {
			//re-subsribe if we resumed
			if (!networkManager.isSubscribed(this)) {
				networkManager.subscribe(this);
			}
			super.onResume();
		}

		@Override
		public void onPause() {
			//unsubscribe on pause - not to catch any callbacks in the background
			networkManager.unsubscribe(this);
			super.onPause();
		}

		@Override
		public void onStop() {
			// unsubscribe on stop not to leave something behind when Fragment stops
			networkManager.unsubscribe(this);
			super.onStop();
		}

		private void doTestNetwork() {
			myTestNetworkMessage = new NetworkMessage(true); //will be deleted on failure

			List<NameValuePair> values = new LinkedList<NameValuePair>();
			values.add(new BasicNameValuePair("myTestParam1", "param1"));
			values.add(new BasicNameValuePair("myTestParam2", "param2"));

			resultMessage.setURI(URI.create("http://www.myTestServer/myTestRESTPoint/myTestRESTMethod"));
			resultMessage.setMethod("GET");
			resultMessage.setParametersList(values);

			networkManager.putMessage(myTestNetworkMessage);
			networkManager.releaseQueue();
		}

		public void queueStart() {
			if (progressDialog != null) {
				return;
			}

			progressDialog = ProgressDialog.show(this, "Loading", "Please wait...");
		}

		public void queueFinish() {
			if (progressDialog != null && shouldDismissLoading) {
				progressDialog.dismiss();
				progressDialog = null;
			}
		}

		public void queueFailed() {
			if (progressDialog != null) {
				progressDialog.dismiss();
				progressDialog = null;
			}
		}

		public void requestSuccess(NetworkMessage message, NetworkResponse response) {
			if (response == null) {
				return;
			}

			String responseData = null;
			try {
				//try to obtaine response body string.
				//done here so that all messages could work with response body
				responseData = response.getResponseBody();
			} catch (NetworkResponseProcessException e) {
				//if body was null an Exception will be thrown - go to the failure in that case
				requestFail(message, response);
				return;
			}


			if (message.equals(myTestNetworkMessage)) {
				//Our message was sent and we successfully obtained a response
				//Now we can use Gson to parse response or do that manually
			}
			
		}

		public void requestFail(NetworkMessage message, NetworkResponse response) {
			//TODO: display error message
		}

		public void requestProgress(NetworkMessage message) {
			//This callback will rarely be used, I think
			//But just in case, you can update a custom progress bar here, for example
		}

	}

As you can see - nothing difficult.

Get NetworkManager instance, implement NetworkListener and subscribe to events.

Create a few NetworkMessages, put them into the manager and call releaseQueue() method.

Wait for manager callbacks to fire and process the response.


I would also recommend to put all network URIs to a separate interface as constants, and implement a wrapper that will build your network messages.

In my project it was named MessageBuilder and was responsible for JSON covertions, NetworkMessage preparations with properly named arguments.


## A few more words

Code is commented, so if you have any questions please refer to javadoc documentation in the code.

Of course, you can also just check the sources - everything there is quite trivial.

Please open an issue if you find any bugs, I'll try to react as soon as I have time.


If you will use this NetworkManager implementation in your project - please send me a note, I will be glad to hear that my code was of use to someone.
