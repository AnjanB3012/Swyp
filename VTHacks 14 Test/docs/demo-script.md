# 60-second judging demo

1. **0–8 seconds:** On Android, show Kroger and $200. Point out that all three demo cards were evaluated.
2. **8–18 seconds:** Show Amex Blue Cash selected automatically: 4% grocery rewards and 21% projected utilization. Explain that Chase's 5% reward loses after its 34% utilization penalty.
3. **18–25 seconds:** On iPhone, select $1.00 and tap **Pay**. The terminal says to hold the customer device near the top.
4. **25–38 seconds:** Place the upper backs of the phones together and slowly adjust until the iPhone detects the Android HCE target. Keep them still through “Reading credential” and “Authorizing.”
5. **38–47 seconds:** Show “APPROVED” on iPhone with the selected display name/last four and “PAID” on Android.
6. **47–60 seconds:** Explain: “The iPhone sent an amount, terminal ID, timestamp, and fresh random nonce. Android signed the exact transaction with a non-exportable Keystore key. This demo credential layer is modular; production replaces it with issuer/network-approved secure provisioning and certified acceptance.”

Before judging, do one cold-start tap on the actual pair of phones and learn each device's antenna sweet spot. Remove thick or magnetic cases if detection is inconsistent.

