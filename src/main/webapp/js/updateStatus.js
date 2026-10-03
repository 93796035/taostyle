// the featureId could be feature.id or the index of the feature.
function updateStatus(imageKey, featureId, user_role, change, curObj, taxRate) {
    if ('[ROLE_ADMIN]' === user_role || user_role === '[ROLE_MANAGER]') {
        // Manager or admin: update feature
        $.ajax({
            headers: { "Accept": "application/json" },
            type: "POST",
            url: "updateFeature/" + imageKey,
            data: "featureId=" + featureId,
            async: false,
            success: function () {
                let check = document.getElementById("check-" + imageKey);
                let opacity = check.checked ? 1 : 0.1;

                let img = document.getElementById("img-" + imageKey);
                if (img) img.style.opacity = opacity;

                let desc = document.getElementById("des-" + imageKey);
                if (desc) desc.style.opacity = opacity;
            }
        });
    } else if ('[ROLE_PRINTER]' === user_role) {
        // Printer role: update printer
        $.ajax({
            headers: { "Accept": "application/json" },
            type: "POST",
            url: "updatePrinter/" + imageKey,
            data: "featureId=" + featureId,
            async: false,
            success: function () {
                let check = document.getElementById("check-" + imageKey);
                let opacity = check.checked ? 1 : 0.1;

                let img = document.getElementById("img-" + imageKey);
                if (img) img.style.opacity = opacity;

                let desc = document.getElementById("des-" + imageKey);
                if (desc) desc.style.opacity = opacity;
            }
        });
    } else {
        curObj = curObj || document.getElementById("check-" + imageKey);
        let textField = document.getElementById("txt-" + imageKey);
        let selected = textField ? Number(textField.value) : (curObj.checked ? 1 : 0);

        let rawNew, rawOld, parsedNew, parsedOld, delta;

        if (change === 'set' && textField) {
            rawNew = textField.value;
            parsedNew = parseInt(rawNew);
            rawOld = textField.dataset.oldValue || '0';
            parsedOld = parseInt(rawOld);

            if (isNaN(parsedNew) || parsedNew < 0) {
                textField.value = '0';
                return;
            }

            selected = parsedNew;
            delta = parsedNew - parsedOld;

            if (delta !== 0) {
                let selectedItem = document.getElementById("selectedItem");
                if (selectedItem) {
                    selectedItem.innerText = Number(selectedItem.innerText) + delta;
                }

                let totalPrice = document.getElementById("totalPrice");
                let description = document.getElementById("price-" + imageKey);
                if (totalPrice && description) {
                    let text = description.innerText;
                    let i = text.lastIndexOf('$');
                    let unitPrice = Number(text.substring(i + 1)) * (1 + taxRate / 100);
                    let total = Number(totalPrice.innerText);
                    totalPrice.innerText = (total + delta * unitPrice).toFixed(2);
                }
            }
            textField.dataset.oldValue = textField.value;
        }

        if (selected === 0 && change !== 'up1' && change !== 'check') return;

        let description = document.getElementById("des-" + imageKey);
        if (description) description.style.opacity = selected > 0 ? 1 : 0.1;

        let image = document.getElementById("img-" + imageKey);
        if (image) image.style.opacity = selected > 0 ? 1 : 0.1;

        if (typeof selectedItems !== 'undefined') {
            let selectedItem = document.getElementById("selectedItem");
            let totalPrice = document.getElementById("totalPrice");

            if (selectedItem && totalPrice && description) {
                let itemNum = Number(selectedItem.innerText);
                let total = Number(totalPrice.innerText);

                let text = description.innerText;
                let i = text.lastIndexOf('$');
                let selectedPrice = Number(text.substring(i + 1)) * (1 + taxRate / 100);

                if (change.indexOf('down') === 0) {
                    let amount = Number(change.substring(4));
                    selected -= amount;
                    textField.value = selected;
                    selectedItem.innerText = itemNum - amount;
                    totalPrice.innerText = (total - selectedPrice).toFixed(2);
                } else if (change.indexOf('up') === 0) {
                    let amount = Number(change.substring(2));
                    selected += amount;
                    if (textField) textField.value = selected;
                    selectedItem.innerText = itemNum + amount;
                    totalPrice.innerText = (total + selectedPrice).toFixed(2);
                } else if (change === "check") {
                    selected = 1 - selected;
                    if (textField) textField.value = selected;
                    selectedItem.innerText = itemNum + (curObj.checked ? 1 : -1);
                    totalPrice.innerText = (curObj.checked ? (total + selectedPrice) : (total - selectedPrice)).toFixed(2);
                }
            }
        }

        let checkBox = document.getElementById("check-" + featureId + "-" + imageKey);
        if (checkBox) {
            checkBox.style.visibility = selected > 1 ? 'hidden' : 'visible';
        }

        let note = document.getElementById("note-" + imageKey);
        if (note) {
            note.style.visibility = (selected >= 2 || change.indexOf('up') === 0) ? 'visible' : 'hidden';
        }

        // Update session data (via AJAX)
        $.ajax({
            headers: { "Accept": "application/json" },
            type: "POST",
            url: "updateSelection/" + imageKey,
            data: (change === 'set' &amp;&amp; textField)
                ? "change=set:old=" + rawOld + ",new=" + rawNew
                : "change=" + change,
            async: true,
            success: function (response) {
                // 更新文本框
                if (textField) {
                    var newCount = response.selectedItems &amp;&amp; response.selectedItems[imageKey]
                        ? response.selectedItems[imageKey]
                        : 0;
                    textField.value = newCount;
                    textField.dataset.oldValue = newCount;
                }

                // ✅ 用后端返回的 itemNumber 更新 DOM
                var selectedItem = document.getElementById("selectedItem");
                if (selectedItem) {
                    selectedItem.innerText = response.itemNumber;
                }

                // ✅ 用后端返回的 totalPrice
                var totalPrice = document.getElementById("totalPrice");
                if (totalPrice) {
                    totalPrice.innerText = parseFloat(response.totalPrice).toFixed(2);
                }
            }
        });
    }
}
