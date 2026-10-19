package io.github.ozozorz.aimaid.llm;

/** Run the real chat/network/UI workflow with a four-step model-authored plan instead of the legacy preset. */
public class ActionPlanClientGameTest extends WheatDeliveryClientGameTest {
    @Override protected boolean plans() { return true; }
}
