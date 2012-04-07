function initSections(initHidden) {
    if (initHidden == "hidden") {
        jQuery(".togglebleTitle").next().hide();
        jQuery(".togglebleTitle").addClass("contentHidden");
        jQuery(".togglebleTitle").each(
                function() {
                    jQuery(this).html(jQuery(this).html() + '<img src="../admin/images/arrow-up.png" />');
                }
                );
    } else {
        jQuery(".togglebleTitle").next().show();
        jQuery(".togglebleTitle").removeClass("contentHidden");
        jQuery(".togglebleTitle").each(
                function() {
                    jQuery(this).html(jQuery(this).html() + '<img src="../admin/images/arrow-down.png" />');
                }
                );
    }
    jQuery(".togglebleTitle").click(
            function() {
                if (jQuery(this).next().is(":visible")) {
                    jQuery(this).addClass("contentHidden");
                    jQuery('img', this).remove();
                    jQuery(this).html(jQuery(this).html() + '<img src="../admin/images/arrow-up.png" />');
                } else {
                    jQuery(this).removeClass("contentHidden");
                    jQuery('img', this).remove();
                    jQuery(this).html(jQuery(this).html() + '<img src="../admin/images/arrow-down.png" />');

                }
                jQuery(this).next().toggle("fast");
            }
            );
}
function createPlaceholders(){
    var inputs = jQuery("input[type=text],input[type=email],input[type=tel],input[type=url]");
    inputs.each(
        function(){
            var _this = jQuery(this);
            this.placeholderVal = _this.attr("placeholder");
            _this.val(this.placeholderVal);
            if(this.placeholderVal != ""){
                _this.addClass("placeholderClass");
            }
        }
    )
    .bind("focus",function(){
        var _this = jQuery(this);
        var val = jQuery.trim(_this.val());
        if(val==this.placeholderVal || val == ""){
            _this.val("");
            _this.removeClass("placeholderClass");
        }
    })
    .bind("blur",function(){
        var _this = jQuery(this);
        var val = jQuery.trim(_this.val());
        if(val == this.placeholderVal || val == ""){
            _this.val(this.placeholderVal);
            _this.addClass("placeholderClass");
        }

    });
}