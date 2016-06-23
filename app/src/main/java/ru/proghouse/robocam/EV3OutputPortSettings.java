package ru.proghouse.robocam;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;

import org.w3c.dom.Element;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link EV3OutputPortSettings.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link EV3OutputPortSettings#newInstance} factory method to
 * create an instance of this fragment.
 */
public class EV3OutputPortSettings extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String GROUP = "group";
    private static final String LAYER = "layer";
    private static final String NAME = "name";

    // TODO: Rename and change types of parameters
    private int group;
    private int layer;
    private String name;

    private OnFragmentInteractionListener mListener;

    public static EV3OutputPortSettings newInstance(int group, int layer, String name) {
        EV3OutputPortSettings fragment = new EV3OutputPortSettings();
        Bundle args = new Bundle();
        args.putInt(GROUP, group);
        args.putInt(LAYER, layer);
        args.putString(NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    public EV3OutputPortSettings() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            group = getArguments().getInt(GROUP);
            layer = getArguments().getInt(LAYER);
            name = getArguments().getString(NAME);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_ev3_output_port_settings, container, false);
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
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
