package info.fandroid.navdrawer.fragments;

import android.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLSocketFactory;

import info.fandroid.navdrawer.Chat.Message;
import info.fandroid.navdrawer.Chat.MessagesListAdapter;
import info.fandroid.navdrawer.Chat.Utils;
import info.fandroid.navdrawer.MainActivity;
import info.fandroid.navdrawer.R;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link FragmentChat.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * create an instance of this fragment.
 */
public class FragmentChat extends Fragment {
    private static final String TAG = MainActivity.class.getSimpleName();

    View view;
    Button btnSend;
    EditText inputMsg;
    List<Message> listMessages;
    ListView listViewMessages;
    MessagesListAdapter adapter;

    Utils utils;
    WebSocketClient client;

    LayoutInflater thisInflater;
    ViewGroup container;

    String ws_uri = "ws://176.112.197.64:19888/room/1/ws_chat";
    String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE0NjExMDU2MTh9.Wif7_a1rqAUU_dsfQR77n5P7xnlRrkzihe4GfgE6Usk";

    private static final String TAG_SELF = "self", TAG_NEW = "new",
            TAG_MESSAGE = "message", TAG_EXIT = "exit";

    private OnFragmentInteractionListener mListener;

    public FragmentChat() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        super.onCreateView(inflater, container, savedInstanceState);

        thisInflater = inflater;
        this.container = container;

        btnSend = (Button) view.findViewById(R.id.send_button);
        inputMsg = (EditText) view.findViewById(R.id.message_input);
        listViewMessages = (ListView) view.findViewById(R.id.list_view_messages);

        listMessages = new ArrayList<Message>();

        adapter = new MessagesListAdapter(container.getContext(), listMessages);
        listViewMessages.setAdapter(adapter);

        btnSend.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // Sending message to web socket server
                String message = inputMsg.getText().toString();
                sendMessageToServer(utils.getSendMessageJSON(message));

                // Clearing the input filed once message was sent
                inputMsg.setText("");
            }
        });

        utils = new Utils(inflater.getContext().getApplicationContext());

        /**
         * Creating web socket client. This will have callback methods
         * */
        URI uri = URI.create(ws_uri);

        client = new WebSocketClient(uri, new Draft_17()) {

            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                sendMessageToServer(token);
            }

            /**
             * On receiving the message from web socket server
             * */
            @Override
            public void onMessage(String message) {
                parseMessage(message);
            }

            /**
             * Called when the connection is terminated
             * */
            @Override
            public void onClose(int code, String reason, boolean b) {

                String message = String.format(Locale.US,
                        "Disconnected! Code: %d Reason: %s / %s", code, reason, b);

                Log.d(TAG, message);

                showToast(message);

                // clear the session id from shared preferences
                utils.storeSessionId(null);
            }

            @Override
            public void onError(Exception error) {
                Log.d(TAG, "Error! : " + error);
                showToast("Error! : " + error);
            }

        };

        client.connect();

        return view;
    }

    private void sendMessageToServer(String message) {
        if (client != null) {
            client.send(message);
        }
    }

    private void parseMessage(final String msg) {
        try {
            JSONObject jObj = new JSONObject(msg);

            // JSON node 'flag'
            String flag = jObj.getString("flag");

            // if flag is 'self', this JSON contains session id
            if (flag.equalsIgnoreCase(TAG_SELF)) {

                String sessionId = jObj.getString("sessionId");

                // Save the session id in shared preferences
                utils.storeSessionId(sessionId);

            } else if (flag.equalsIgnoreCase(TAG_NEW)) {
                // If the flag is 'new', new person joined the room
                String name = jObj.getString("name");
                String message = jObj.getString("message");

                // number of people online
                String onlineCount = jObj.getString("onlineCount");

                showToast(name + message + ". Currently " + onlineCount
                        + " people online!");

            } else if (flag.equalsIgnoreCase(TAG_MESSAGE)) {
                // if the flag is 'message', new message received
                String fromName = jObj.getString("author");
                String message = jObj.getString("message");
                boolean isSelf = false;

                if (fromName == "test") {
                    isSelf = true;
                }

                Message m = new Message(fromName, message, isSelf);

                // Appending the message to chat list
                appendMessage(m);

            } else if (flag.equalsIgnoreCase(TAG_EXIT)) {
                // If the flag is 'exit', somebody left the conversation
                String name = jObj.getString("name");
                String message = jObj.getString("message");

                showToast(name + message);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void showToast(final String message) {
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(thisInflater.getContext().getApplicationContext(), message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void appendMessage(final Message m) {
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                listMessages.add(m);
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

}
