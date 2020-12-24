package rs.ltt.android.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import rs.ltt.android.R;
import rs.ltt.android.databinding.FragmentLabelAsBinding;
import rs.ltt.android.entity.SelectableMailbox;
import rs.ltt.android.ui.adapter.ChooseLabelsAdapter;
import rs.ltt.android.ui.model.ChooseLabelsViewModel;

public class ChooseLabelsFragment extends AbstractLttrsFragment implements ChooseLabelsAdapter.OnSelectableMailboxClickListener {

    private FragmentLabelAsBinding binding;

    private ChooseLabelsAdapter chooseLabelsAdapter;
    private ChooseLabelsViewModel viewModel;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        getLttrsViewModel().setActivityTitle(R.string.label_as);
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_label_as, container, false);

        this.chooseLabelsAdapter = new ChooseLabelsAdapter();
        this.chooseLabelsAdapter.setOnSelectableMailboxClickListener(this);

        binding.labelList.setAdapter(this.chooseLabelsAdapter);

        binding.cancel.setOnClickListener(this::onCancel);

        final ChooseLabelsFragmentArgs arguments = ChooseLabelsFragmentArgs.fromBundle(requireArguments());

        final ViewModelProvider viewModelProvider = new ViewModelProvider(
                getViewModelStore(),
                new ChooseLabelsViewModel.Factory(
                        requireActivity().getApplication(),
                        getLttrsViewModel().getAccountId(),
                        arguments.getThreads()
                )
        );
        this.viewModel = viewModelProvider.get(ChooseLabelsViewModel.class);
        this.viewModel.getSelectableMailboxesLiveData().observe(getViewLifecycleOwner(), this.chooseLabelsAdapter::submitList);
        return binding.getRoot();
    }

    private void onCancel(final View view) {
        getNavController().navigateUp();
    }

    @Override
    public void onSelectableMailboxClick(final SelectableMailbox mailbox) {
        this.viewModel.setSelectionOverwrite(mailbox, !mailbox.isSelected());
    }
}
