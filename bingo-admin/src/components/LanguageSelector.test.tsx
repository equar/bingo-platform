import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { I18nextProvider, useTranslation } from "react-i18next";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import i18n from "../i18n";
import { LanguageSelector } from "./LanguageSelector";

function TestScreen() {
  const { t } = useTranslation();
  const [value, setValue] = useState("keep this");

  return (
    <>
      <LanguageSelector />
      <input aria-label="test form input" value={value} onChange={(event) => setValue(event.target.value)} />
      <div>{t("nav.dashboard")}</div>
    </>
  );
}

describe("LanguageSelector", () => {
  beforeEach(async () => {
    window.localStorage.clear();
    await i18n.changeLanguage("en");
  });

  afterEach(async () => {
    cleanup();
    window.localStorage.clear();
    await i18n.changeLanguage("en");
  });

  it("switches immediately, keeps form input, and persists the selection", async () => {
    const user = userEvent.setup();
    render(
      <I18nextProvider i18n={i18n}>
        <TestScreen />
      </I18nextProvider>,
    );

    await user.click(screen.getByRole("combobox", { name: "Language" }));
    await user.click(screen.getByRole("option", { name: "አማርኛ" }));

    await waitFor(() => {
      expect(screen.getByText("ዳሽቦርድ")).toBeInTheDocument();
      expect(window.localStorage.getItem("bingo-admin-locale")).toBe("am");
    });
    expect(screen.getByRole("textbox", { name: "test form input" })).toHaveValue("keep this");
  });
});
