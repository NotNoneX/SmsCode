package com.github.tianma8023.smscode.app.record;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.github.tianma8023.smscode.R;
import com.github.tianma8023.smscode.adapter.BaseItemCallback;
import com.github.tianma8023.smscode.app.base.BackPressFragment;
import com.github.tianma8023.smscode.db.DBManager;
import com.github.tianma8023.smscode.entity.SmsMsg;
import com.github.tianma8023.smscode.utils.ClipboardUtils;
import com.github.tianma8023.smscode.utils.XLog;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * SMS code records fragment
 */
public class CodeRecordsFragment extends BackPressFragment {

    // normal mode
    private static final int RECORD_MODE_NORMAL = 0;
    // edit mode
    private static final int RECORD_MODE_EDIT = 1;

    @IntDef({RECORD_MODE_NORMAL, RECORD_MODE_EDIT})
    @interface RecordMode {
    }

    private Activity mActivity;

    @BindView(R.id.code_records_recycler_view)
    RecyclerView mRecyclerView;

    private CodeRecordAdapter mCodeRecordAdapter;

    private @RecordMode
    int mCurrentMode = RECORD_MODE_NORMAL;

    public static CodeRecordsFragment newInstance() {
        return new CodeRecordsFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_code_records, container, false);
        ButterKnife.bind(this, rootView);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = getActivity();

        List<SmsMsg> smsMsgList = DBManager.get(mActivity).queryAllSmsMsg();
        List<RecordItem> records = new ArrayList<>();
        for (SmsMsg smsMsg : smsMsgList) {
            records.add(new RecordItem(smsMsg, false));
        }

        mCodeRecordAdapter = new CodeRecordAdapter(mActivity, records);
        mCodeRecordAdapter.setItemCallback(new BaseItemCallback<RecordItem>() {
            @Override
            public void onItemClicked(View itemView, RecordItem item, int position) {
                itemClicked(item, position);
            }

            @Override
            public boolean onItemLongClicked(View itemView, RecordItem item, int position) {
                return itemLongClicked(item, position);
            }
        });

        mRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        mRecyclerView.setAdapter(mCodeRecordAdapter);
    }

    private void itemClicked(RecordItem item, int position) {
        if (mCurrentMode == RECORD_MODE_EDIT) {
            itemLongClicked(item, position);
        } else {
            copySmsCode(item);
        }
    }

    private void copySmsCode(RecordItem item) {
        String smsCode = item.getSmsMsg().getSmsCode();
        ClipboardUtils.copyToClipboard(mActivity, smsCode);
        String prompt = getString(R.string.prompt_sms_code_copied, smsCode);
        Snackbar.make(mRecyclerView, prompt, Snackbar.LENGTH_SHORT).show();
    }

    private boolean itemLongClicked(RecordItem item, int position) {
        selectRecordItem(position);
        return true;
    }

    private void selectRecordItem(int position) {
        if (mCurrentMode == RECORD_MODE_NORMAL) {
            mCurrentMode = RECORD_MODE_EDIT;
            refreshActionBarByMode();
        }
        boolean selected = mCodeRecordAdapter.isItemSelected(position);
        mCodeRecordAdapter.setItemSelected(position, !selected);
        if (selected) {
            boolean isAllUnselected = mCodeRecordAdapter.isAllUnselected();
            if (isAllUnselected) {
                mCurrentMode = RECORD_MODE_NORMAL;
                refreshActionBarByMode();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mCurrentMode == RECORD_MODE_EDIT) {
            inflater.inflate(R.menu.menu_edit_code_record, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                removeSelectedItems();
                break;
            case R.id.action_select_all:
                boolean isAllSelected = mCodeRecordAdapter.isAllSelected();
                mCodeRecordAdapter.setAllSelected(!isAllSelected);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onInterceptBackPressed() {
        return mCurrentMode == RECORD_MODE_EDIT;
    }

    @Override
    public void onBackPressed() {
        if (mCurrentMode == RECORD_MODE_EDIT) {
            mCurrentMode = RECORD_MODE_NORMAL;
            mCodeRecordAdapter.setAllSelected(false);
            refreshActionBarByMode();
        } else {
            super.onBackPressed();
        }
    }

    private void removeSelectedItems() {
        final List<SmsMsg> itemsToRemove = mCodeRecordAdapter.removeSelectedItems();
        String text = getString(R.string.some_items_removed, itemsToRemove.size());
        Snackbar snackbar = Snackbar.make(mRecyclerView, text, Snackbar.LENGTH_LONG);
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    try {
                        DBManager.get(mActivity).removeSmsMsgList(itemsToRemove);
                    } catch (Exception e) {
                        XLog.e("Error occurs when remove SMS records", e);
                    }
                }
            }
        });
        snackbar.setAction(R.string.revoke, new View.OnClickListener() {
            @Override
            public void onClick(View v){
                mCodeRecordAdapter.addItems(itemsToRemove);
            }
        });
        snackbar.show();

        mCurrentMode = RECORD_MODE_NORMAL;
        refreshActionBarByMode();
    }

    private void refreshActionBarByMode() {
        if (mCurrentMode == RECORD_MODE_NORMAL) {
            mActivity.setTitle(R.string.smscode_records);
            mActivity.invalidateOptionsMenu();
        } else {
            mActivity.setTitle(R.string.edit_smscode_records);
            mActivity.invalidateOptionsMenu();
        }
    }
}