/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.activities;

import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.Toast;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.R;
import com.android.contacts.common.activity.RequestPermissionsActivity;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.DirectoryListLoader;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.list.ContactPickerFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.EmailAddressPickerFragment;
import com.android.contacts.list.GroupMemberPickerFragment;
import com.android.contacts.list.JoinContactListFragment;
import com.android.contacts.list.LegacyPhoneNumberPickerFragment;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.list.MultiSelectContactsListFragment.OnCheckBoxListActionListener;
import com.android.contacts.list.OnContactPickerActionListener;
import com.android.contacts.list.OnEmailAddressPickerActionListener;
import com.android.contacts.list.OnPostalAddressPickerActionListener;
import com.android.contacts.list.PostalAddressPickerFragment;
import com.android.contacts.list.UiIntentActions;

import java.util.ArrayList;

/**
 * Displays a list of contacts (or phone numbers or postal addresses) for the
 * purposes of selecting one.
 */
public class ContactSelectionActivity extends AppCompatContactsActivity implements
        View.OnCreateContextMenuListener, ActionBarAdapter.Listener, OnClickListener,
        OnFocusChangeListener, OnCheckBoxListActionListener {
    private static final String TAG = "ContactSelection";

    private static final String KEY_ACTION_CODE = "actionCode";
    private static final String KEY_SEARCH_MODE = "searchMode";
    private static final int DEFAULT_DIRECTORY_RESULT_LIMIT = 20;

    private ContactsIntentResolver mIntentResolver;
    protected ContactEntryListFragment<?> mListFragment;

    private int mActionCode = -1;
    private boolean mIsSearchMode;
    private boolean mIsSearchSupported;

    private ContactsRequest mRequest;

    private ActionBarAdapter mActionBarAdapter;
    private Toolbar mToolbar;

    public ContactSelectionActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactEntryListFragment<?>) {
            mListFragment = (ContactEntryListFragment<?>) fragment;
            setupActionListener();
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        if (savedState != null) {
            mActionCode = savedState.getInt(KEY_ACTION_CODE);
            mIsSearchMode = savedState.getBoolean(KEY_SEARCH_MODE);
        }

        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        setContentView(R.layout.contact_picker);

        if (mActionCode != mRequest.getActionCode()) {
            mActionCode = mRequest.getActionCode();
            configureListFragment();
        }

        prepareSearchViewAndActionBar(savedState);
        configureActivityTitle();
    }

    public boolean isSelectionMode() {
        return mActionBarAdapter.isSelectionMode();
    }

    public boolean isSearchMode() {
        return mActionBarAdapter.isSearchMode();
    }

    private void prepareSearchViewAndActionBar(Bundle savedState) {
        mToolbar = getView(R.id.toolbar);
        setSupportActionBar(mToolbar);

        // Add a shadow under the toolbar.
        ViewUtil.addRectangularOutlineProvider(findViewById(R.id.toolbar_parent), getResources());

        mActionBarAdapter = new ActionBarAdapter(this, this, getSupportActionBar(),
                /* portraitTabs */ null, /* landscapeTabs */ null, mToolbar,
                R.string.enter_contact_name);
        mActionBarAdapter.setShowHomeIcon(true);
        mActionBarAdapter.setShowHomeAsUp(true);
        mActionBarAdapter.setTransparentStatuBar(false);
        mActionBarAdapter.initialize(savedState, mRequest);

        // Postal address pickers (and legacy pickers) don't support search, so just show
        // "HomeAsUp" button and title.
        if (mRequest.getActionCode() == ContactsRequest.ACTION_PICK_POSTAL ||
                mRequest.isLegacyCompatibilityMode()) {
            mIsSearchSupported = false;
        } else {
            mIsSearchSupported = true;
        }
        configureSearchMode();
    }

    private void configureSearchMode() {
        mActionBarAdapter.setSearchMode(mIsSearchMode);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Go back to previous screen, intending "cancel"
                setResult(RESULT_CANCELED);
                onBackPressed();
                return true;
            case R.id.menu_search:
                mIsSearchMode = !mIsSearchMode;
                configureSearchMode();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTION_CODE, mActionCode);
        outState.putBoolean(KEY_SEARCH_MODE, mIsSearchMode);
        if (mActionBarAdapter != null) {
            mActionBarAdapter.onSaveInstanceState(outState);
        }
    }

    private void configureActivityTitle() {
        if (!TextUtils.isEmpty(mRequest.getActivityTitle())) {
            getSupportActionBar().setTitle(mRequest.getActivityTitle());
            return;
        }
        int titleResId = -1;
        int actionCode = mRequest.getActionCode();
        switch (actionCode) {
            case ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT: {
                titleResId = R.string.contactInsertOrEditActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_PICK_CONTACT: {
                titleResId = R.string.contactPickerActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT: {
                titleResId = R.string.contactPickerActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT: {
                titleResId = R.string.shortcutActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_PICK_PHONE: {
                titleResId = R.string.contactPickerActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_PICK_EMAIL: {
                titleResId = R.string.contactPickerActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_CREATE_SHORTCUT_CALL: {
                titleResId = R.string.callShortcutActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_CREATE_SHORTCUT_SMS: {
                titleResId = R.string.messageShortcutActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_PICK_POSTAL: {
                titleResId = R.string.contactPickerActivityTitle;
                break;
            }
            case ContactsRequest.ACTION_PICK_JOIN: {
                titleResId = R.string.titleJoinContactDataWith;
                break;
            }
            case ContactsRequest.ACTION_PICK_GROUP_MEMBERS: {
                titleResId = R.string.groupMemberPickerActivityTitle;
                break;
            }
        }
        if (titleResId > 0) {
            getSupportActionBar().setTitle(titleResId);
        }
    }

    /**
     * Creates the fragment based on the current request.
     */
    public void configureListFragment() {
        switch (mActionCode) {
            case ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setEditMode(true);
                fragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
                fragment.setCreateContactEnabled(!mRequest.isSearchMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_DEFAULT:
            case ContactsRequest.ACTION_PICK_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setIncludeFavorites(mRequest.shouldIncludeFavorites());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setCreateContactEnabled(!mRequest.isSearchMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setShortcutRequested(true);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_PHONE: {
                PhoneNumberPickerFragment fragment = getPhoneNumberPickerFragment(mRequest);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_EMAIL: {
                mListFragment = new EmailAddressPickerFragment();
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CALL: {
                PhoneNumberPickerFragment fragment = getPhoneNumberPickerFragment(mRequest);
                fragment.setShortcutAction(Intent.ACTION_CALL);

                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_SMS: {
                PhoneNumberPickerFragment fragment = getPhoneNumberPickerFragment(mRequest);
                fragment.setShortcutAction(Intent.ACTION_SENDTO);

                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_POSTAL: {
                PostalAddressPickerFragment fragment = new PostalAddressPickerFragment();

                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_JOIN: {
                JoinContactListFragment joinFragment = new JoinContactListFragment();
                joinFragment.setTargetContactId(getTargetContactId());
                mListFragment = joinFragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_GROUP_MEMBERS: {
                final String accountName = getIntent().getStringExtra(
                        UiIntentActions.GROUP_ACCOUNT_NAME);
                final String accountType = getIntent().getStringExtra(
                        UiIntentActions.GROUP_ACCOUNT_TYPE);
                final String accountDataSet = getIntent().getStringExtra(
                        UiIntentActions.GROUP_ACCOUNT_DATA_SET);
                final ArrayList<String> contactIds = getIntent().getStringArrayListExtra(
                        UiIntentActions.GROUP_CONTACT_IDS);
                mListFragment = GroupMemberPickerFragment.newInstance(
                        accountName, accountType, accountDataSet, contactIds);
                break;
            }

            default:
                throw new IllegalStateException("Invalid action code: " + mActionCode);
        }

        // Setting compatibility is no longer needed for PhoneNumberPickerFragment since that logic
        // has been separated into LegacyPhoneNumberPickerFragment.  But we still need to set
        // compatibility for other fragments.
        mListFragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
        mListFragment.setDirectoryResultLimit(DEFAULT_DIRECTORY_RESULT_LIMIT);

        getFragmentManager().beginTransaction()
                .replace(R.id.list_container, mListFragment)
                .commitAllowingStateLoss();
    }

    private PhoneNumberPickerFragment getPhoneNumberPickerFragment(ContactsRequest request) {
        if (mRequest.isLegacyCompatibilityMode()) {
            return new LegacyPhoneNumberPickerFragment();
        } else {
            return new PhoneNumberPickerFragment();
        }
    }

    public void setupActionListener() {
        if (mListFragment instanceof ContactPickerFragment) {
            ((ContactPickerFragment) mListFragment).setOnContactPickerActionListener(
                    new ContactPickerActionListener());
        } else if (mListFragment instanceof PhoneNumberPickerFragment) {
            ((PhoneNumberPickerFragment) mListFragment).setOnPhoneNumberPickerActionListener(
                    new PhoneNumberPickerActionListener());
        } else if (mListFragment instanceof PostalAddressPickerFragment) {
            ((PostalAddressPickerFragment) mListFragment).setOnPostalAddressPickerActionListener(
                    new PostalAddressPickerActionListener());
        } else if (mListFragment instanceof EmailAddressPickerFragment) {
            ((EmailAddressPickerFragment) mListFragment).setOnEmailAddressPickerActionListener(
                    new EmailAddressPickerActionListener());
        } else if (mListFragment instanceof JoinContactListFragment) {
            ((JoinContactListFragment) mListFragment).setOnContactPickerActionListener(
                    new JoinContactActionListener());
        } else if (mListFragment instanceof GroupMemberPickerFragment) {
            ((GroupMemberPickerFragment) mListFragment).setListener(
                    new GroupMemberPickerListener());
            getMultiSelectListFragment().setCheckBoxListListener(this);
        } else {
            throw new IllegalStateException("Unsupported list fragment type: " + mListFragment);
        }
    }

    private MultiSelectContactsListFragment getMultiSelectListFragment() {
        if (mListFragment instanceof MultiSelectContactsListFragment) {
            return (MultiSelectContactsListFragment) mListFragment;
        }
        return null;
    }

    @Override
    public void onAction(int action) {
        switch (action) {
            case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
                mIsSearchMode = true;
                configureSearchMode();
                break;
            case ActionBarAdapter.Listener.Action.CHANGE_SEARCH_QUERY:
                final String queryString = mActionBarAdapter.getQueryString();
                mListFragment.setQueryString(queryString, /* delaySelection */ false);
                break;
            case ActionBarAdapter.Listener.Action.START_SELECTION_MODE:
                if (getMultiSelectListFragment() != null) {
                    getMultiSelectListFragment().displayCheckBoxes(true);
                }
                invalidateOptionsMenu();
                break;
            case ActionBarAdapter.Listener.Action.STOP_SEARCH_AND_SELECTION_MODE:
                mListFragment.setQueryString("", /* delaySelection */ false);
                mActionBarAdapter.setSearchMode(false);
                if (getMultiSelectListFragment() != null) {
                    getMultiSelectListFragment().displayCheckBoxes(false);
                }
                invalidateOptionsMenu();
                break;
        }
    }

    @Override
    public void onSelectedTabChanged() {
    }

    @Override
    public void onUpButtonPressed() {
        onBackPressed();
    }

    @Override
    public void onStartDisplayingCheckBoxes() {
        mActionBarAdapter.setSelectionMode(true);
    }

    @Override
    public void onSelectedContactIdsChanged() {
        if (mListFragment instanceof MultiSelectContactsListFragment) {
            mActionBarAdapter.setSelectionCount(((MultiSelectContactsListFragment) mListFragment)
                    .getSelectedContactIds().size());
            // Show or hide the multi select "Done" button
            invalidateOptionsMenu();
        }
    }

    @Override
    public void onStopDisplayingCheckBoxes() {
        mActionBarAdapter.setSelectionMode(false);
    }

    private final class ContactPickerActionListener implements OnContactPickerActionListener {
        @Override
        public void onCreateNewContactAction() {
            startCreateNewContactActivity();
        }

        @Override
        public void onEditContactAction(Uri contactLookupUri) {
            startActivityAndForwardResult(EditorIntents.createEditContactIntent(
                    contactLookupUri, /* materialPalette =*/ null, /* photoId =*/ -1));
        }

        @Override
        public void onPickContactAction(Uri contactUri) {
            returnPickerResult(contactUri);
        }

        @Override
        public void onShortcutIntentCreated(Intent intent) {
            returnPickerResult(intent);
        }
    }

    private final class PhoneNumberPickerActionListener implements
            OnPhoneNumberPickerActionListener {
        @Override
        public void onPickDataUri(Uri dataUri, boolean isVideoCall, int callInitiationType) {
            returnPickerResult(dataUri);
        }

        @Override
        public void onPickPhoneNumber(String phoneNumber, boolean isVideoCall,
                                      int callInitiationType) {
            Log.w(TAG, "Unsupported call.");
        }

        @Override
        public void onShortcutIntentCreated(Intent intent) {
            returnPickerResult(intent);
        }

        @Override
        public void onHomeInActionBarSelected() {
            ContactSelectionActivity.this.onBackPressed();
        }
    }

    private final class JoinContactActionListener implements OnContactPickerActionListener {
        @Override
        public void onPickContactAction(Uri contactUri) {
            Intent intent = new Intent(null, contactUri);
            setResult(RESULT_OK, intent);
            finish();
        }

        @Override
        public void onShortcutIntentCreated(Intent intent) {
        }

        @Override
        public void onCreateNewContactAction() {
        }

        @Override
        public void onEditContactAction(Uri contactLookupUri) {
        }
    }

    private final class GroupMemberPickerListener implements GroupMemberPickerFragment.Listener {

        @Override
        public void onGroupMemberClicked(long contactId) {
            final Intent intent = new Intent();
            intent.putExtra(UiIntentActions.TARGET_CONTACT_ID_EXTRA_KEY, contactId);
            returnPickerResult(intent);
        }

        @Override
        public void onGroupMembersSelected(long[] contactIds) {
            final Intent intent = new Intent();
            intent.putExtra(UiIntentActions.TARGET_CONTACT_IDS_EXTRA_KEY, contactIds);
            returnPickerResult(intent);
        }

        @Override
        public void onSelectGroupMembers() {
            mActionBarAdapter.setSelectionMode(true);
        }
    }

    private final class PostalAddressPickerActionListener implements
            OnPostalAddressPickerActionListener {
        @Override
        public void onPickPostalAddressAction(Uri dataUri) {
            returnPickerResult(dataUri);
        }
    }

    private final class EmailAddressPickerActionListener implements
            OnEmailAddressPickerActionListener {
        @Override
        public void onPickEmailAddressAction(Uri dataUri) {
            returnPickerResult(dataUri);
        }
    }

    public void startActivityAndForwardResult(final Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Forward extras to the new activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "startActivity() failed: " + e);
            Toast.makeText(ContactSelectionActivity.this, R.string.missing_app,
                    Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        switch (view.getId()) {
            case R.id.search_view: {
                if (hasFocus) {
                    mActionBarAdapter.setFocusOnSearchView();
                }
            }
        }
    }

    public void returnPickerResult(Uri data) {
        Intent intent = new Intent();
        intent.setData(data);
        returnPickerResult(intent);
    }

    public void returnPickerResult(Intent intent) {
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.floating_action_button: {
                startCreateNewContactActivity();
                break;
            }
        }
    }

    private long getTargetContactId() {
        Intent intent = getIntent();
        final long targetContactId = intent.getLongExtra(
                UiIntentActions.TARGET_CONTACT_ID_EXTRA_KEY, -1);
        if (targetContactId == -1) {
            Log.e(TAG, "Intent " + intent.getAction() + " is missing required extra: "
                    + UiIntentActions.TARGET_CONTACT_ID_EXTRA_KEY);
            setResult(RESULT_CANCELED);
            finish();
            return -1;
        }
        return targetContactId;
    }

    private void startCreateNewContactActivity() {
        Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
        intent.putExtra(ContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
        startActivityAndForwardResult(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.search_menu, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_search);
        searchItem.setVisible(!mIsSearchMode && mIsSearchSupported);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!isSafeToCommitTransactions()) {
            return;
        }

        if (mIsSearchMode) {
            mIsSearchMode = false;
            configureSearchMode();
        } else if (isSelectionMode()) {
            mActionBarAdapter.setSelectionMode(false);
        } else {
            super.onBackPressed();
        }
    }
}
