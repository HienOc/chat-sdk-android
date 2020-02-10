package co.chatsdk.ui.fragments;

import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.databinding.DataBindingUtil;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import co.chatsdk.core.dao.Keys;
import co.chatsdk.core.dao.User;
import co.chatsdk.core.events.EventType;
import co.chatsdk.core.events.NetworkEvent;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.types.ConnectionType;
import co.chatsdk.core.utils.StringChecker;
import co.chatsdk.ui.R;
import co.chatsdk.ui.databinding.FragmentProfileBinding;
import co.chatsdk.ui.icons.Icons;
import co.chatsdk.ui.utils.AvailabilityHelper;
import co.chatsdk.ui.utils.ToastHelper;
import co.chatsdk.ui.views.IconItemView;
import co.chatsdk.ui.views.SwitchItemView;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * Created by ben on 8/15/17.
 */

public class ProfileFragment extends BaseFragment {

    public static int ProfileDetailRowHeight = 25;
    public static int ProfileDetailMargin = 8;

    protected User user;
    protected boolean startingChat = false;

    protected FragmentProfileBinding b;

    public static ProfileFragment newInstance(User user) {
        ProfileFragment f = new ProfileFragment();

        Bundle b = new Bundle();

        if (user != null) {
            b.putString(Keys.UserId, user.getEntityID());
        }

        f.setArguments(b);
        f.setRetainInstance(true);
        return f;
    }

    protected @LayoutRes int getLayout() {
        return R.layout.fragment_profile;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        b = DataBindingUtil.inflate(inflater, getLayout(), container, false);
        rootView = b.getRoot();

        if (savedInstanceState != null && savedInstanceState.getString(Keys.UserId) != null) {
            user = ChatSDK.db().fetchUserWithEntityID(savedInstanceState.getString(Keys.UserId));
        }

        initViews();
        addListeners();

        return rootView;
    }

    public void addListeners() {
        dm.add(ChatSDK.events().sourceOnMain().filter(NetworkEvent.filterType(EventType.UserMetaUpdated, EventType.UserPresenceUpdated))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(networkEvent -> {
                    if (networkEvent.user.equals(getUser())) {
                        reloadData();
                    }
                }));
        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.UserMetaUpdated))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(networkEvent -> {
                    if (networkEvent.user.equals(getUser())) {
                        reloadData();
                    }
                }));
    }

    public void initViews() {

        setupTouchUIToDismissKeyboard(rootView, R.id.avatarImageView);

        if (ChatSDK.profilePictures() != null) {
            b.avatarImageView2.setOnClickListener(v -> {
                ChatSDK.profilePictures().startProfilePicturesActivity(getContext(), getUser().getEntityID());
            });
        }

        b.backdrop.setImageResource(R.drawable.header2);

        b.appbar.addOnOffsetChangedListener(new ProfileViewOffsetChangeListener(b));


        reloadData();
    }

    protected void setViewVisibility(View view, int visibility) {
        if (view != null) view.setVisibility(visibility);
    }

    protected void setViewVisibility(View view, boolean visible) {
        setViewVisibility(view, visible ? View.VISIBLE : View.INVISIBLE);
    }

    protected void setViewText(TextView textView, String text) {
        if (textView != null) textView.setText(text);
    }

    protected void setRowVisible (View imageView, View textView, boolean visible) {
        setViewVisibility(textView, visible);
        setViewVisibility(imageView, visible);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    protected void block() {
        if (getUser().isMe()) return;

        dm.add(ChatSDK.blocking().blockUser(getUser().getEntityID())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    showSnackbar(R.string.user_blocked);
                },this));
    }

    protected void unblock() {
        if (getUser().isMe()) return;

        dm.add(ChatSDK.blocking().unblockUser(getUser().getEntityID())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    showSnackbar(R.string.user_unblocked);
                }, this));
    }

    protected void toggleBlocked() {
        if (getUser().isMe()) return;

        boolean blocked = ChatSDK.blocking().isBlocked(getUser().getEntityID());
        if (blocked) {
            unblock();
        }
        else {
            block();
        }
    }

    protected void add() {
        if (getUser().isMe()) return;

        dm.add(ChatSDK.contact().addContact(getUser(), ConnectionType.Contact)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    showSnackbar(R.string.contact_added);
                }, this));
    }

    protected void delete() {
        if (getUser().isMe()) return;

        dm.add(ChatSDK.contact().deleteContact(getUser(), ConnectionType.Contact)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    showSnackbar(R.string.contact_deleted);
                }, this));
    }

    protected void toggleContact() {
        if (getUser().isMe()) return;

        boolean isContact = ChatSDK.contact().exists(getUser());
        if (isContact) {
            delete();
        }
        else {
            add();
        }
    }

    public void updateInterface() {

        User user = getUser();

        if (user == null) {
            return;
        }

        boolean isCurrentUser = user.isMe();
        setHasOptionsMenu(isCurrentUser);

        if (isCurrentUser) {
            b.fab.setImageDrawable(Icons.get(Icons.shared().edit, R.color.white));
            b.fab.setOnClickListener(v -> {
                showEditProfileScreen();
            });
        } else {
            b.fab.setImageDrawable(Icons.get(Icons.shared().chat, R.color.white));
            b.fab.setOnClickListener(v -> {
                startChat();
            });
        }

        b.collapsingToolbar.setTitle(user.getName());
        Picasso.get().load(user.getAvatarURL()).into(b.avatarImageView2);

        if (StringChecker.isNullOrEmpty(user.getStatus())) {
            b.statusCardView.setVisibility(View.GONE);
            b.topSpace.setVisibility(View.VISIBLE);
        } else {
            b.topSpace.setVisibility(View.GONE);
            b.statusCardView.setVisibility(View.VISIBLE);
            b.statusTextView2.setText(user.getStatus());
        }

        // Remove the views and add them back in
        b.iconLinearLayout.removeAllViews();
        b.availabilityLinearLayout.removeAllViews();
        b.buttonsLinearLayout.removeAllViews();

        if (!StringChecker.isNullOrEmpty(user.getLocation())) {
            b.iconLinearLayout.addView(IconItemView.create(getContext(), user.getLocation(), Icons.get(Icons.shared().location, R.color.profile_icon_color)));
        }
        if (!StringChecker.isNullOrEmpty(user.getPhoneNumber())) {
            b.iconLinearLayout.addView(IconItemView.create(getContext(), user.getPhoneNumber(), Icons.get(Icons.shared().phone, R.color.profile_icon_color)));
        }
        if (!StringChecker.isNullOrEmpty(user.getEmail())) {
            b.iconLinearLayout.addView(IconItemView.create(getContext(), user.getEmail(), Icons.get(Icons.shared().email, R.color.profile_icon_color)));
        }
        if (!StringChecker.isNullOrEmpty(user.getPresenceSubscription())) {
            b.iconLinearLayout.addView(IconItemView.create(getContext(), user.getPresenceSubscription(), Icons.get(Icons.shared().email, R.color.profile_icon_color)));
            b.iconLinearLayout.addView(IconItemView.create(getContext(), user.getPresenceSubscription(), Icons.get(Icons.shared().email, R.color.profile_icon_color)));
        }

        if (!isCurrentUser) {

            boolean isBlocked = ChatSDK.blocking().isBlocked(getUser().getEntityID());

            b.buttonsLinearLayout.addView(SwitchItemView.create(
                    getContext(),
                    R.string.blocked,
                    Icons.get(Icons.shared().block, R.color.profile_blocked_icon_color),
                    isBlocked,
                    R.style.BlockSwitchTheme,(buttonView, isChecked) -> {
                toggleBlocked();
            }));

            boolean isContact = ChatSDK.contact().exists(getUser());

            b.buttonsLinearLayout.addView(SwitchItemView.create(
                    getContext(),
                    R.string.contact,
                    Icons.get(Icons.shared().contact, R.color.profile_contacts_icon_color),
                    isContact,
                    R.style.ContactSwitchTheme,
                    (buttonView, isChecked) -> {
                toggleContact();
            }));

            String availability = getUser().getAvailability();

            if (!StringChecker.isNullOrEmpty(availability)) {
                b.availabilityCardView.setVisibility(View.VISIBLE);
                b.availabilityLinearLayout.addView(IconItemView.create(
                        getContext(),
                        AvailabilityHelper.stringForAvailability(getContext(), availability),
                        AvailabilityHelper.imageResourceIdForAvailability(availability)));
            } else {
                b.availabilityCardView.setVisibility(View.GONE);
            }
        }
    }

    protected User getUser () {
        return user != null ? user : ChatSDK.currentUser();
    }

    public void showEditProfileScreen() {
        ChatSDK.ui().startEditProfileActivity(getContext(), ChatSDK.currentUserID());
    }

    public void startChat() {
        ChatSDK.ui().startEditProfileActivity(getContext(), ChatSDK.currentUserID());

        showProgressDialog(getString(R.string.creating_thread));

        dm.add(ChatSDK.thread().createThread("", user, ChatSDK.currentUser())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> {
                    dismissProgressDialog();
                    startingChat = false;
                })
                .subscribe(thread -> {
                    ChatSDK.ui().startChatActivityForID(getContext(), thread.getEntityID());
                }, this.snackbarOnErrorConsumer()));
    }

//    @Override
//    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        super.onCreateOptionsMenu(menu, inflater);
//
//        if (!getUser().isMe()) {
//            return;
//        }
//
//        MenuItem item =
//                menu.add(Menu.NONE, R.id.action_chat_sdk_settings, 12, getString(R.string.action_settings));
//        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        item.setIcon(R.drawable.icn_24_settings);
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//
//        /* Cant use switch in the library*/
//        int id = item.getItemId();
//
//        if (id == R.id.action_chat_sdk_settings)
//        {
//            showEditProfileScreen();
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

    @Override
    public void clearData() {

    }

    @Override
    public void reloadData() {
        updateInterface();
    }

    public void setUser (User user) {
        this.user = user;
    }

}