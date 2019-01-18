function slugify(str) {
    return (str || '')
        .trim()
        .toLowerCase()
        .replace(/[ _+'"]/g, '-')
        .replace(/--/g, '-')
        .normalize('NFD').replace(/[^a-z0-9-]/g, '');
}

// build slug from an other field
(function () {
    $('input[slug-for]').each(function() {
        var slugInput = $(this);
        var srcInput = $('#'+slugInput.attr('slug-for'));
        srcInput.change(function() {
            var src = srcInput.val();
            var prevSrc = srcInput.data("prev");
            var oldSlug = slugInput.val();
            if (oldSlug === '' || oldSlug === slugify(prevSrc)) {
                slugInput.val(slugify(src));
            }
            srcInput.data("prev", src);
        });
    });
})();

// http://www.malot.fr/bootstrap-datetimepicker/
(function () {
    $('input.input-datetime').each(function () {
        $(this).datetimepicker({
            language: 'en',
            autoclose: true,
            initialDate: $(this).attr('startDate')
        });
    });
})();

// https://uxsolutions.github.io/bootstrap-datepicker/
(function () {
    var defaultConfig = {
        format: 'dd/mm/yyyy',
        weekStart: 1,
        maxViewMode: 2,
        language: 'en',
        autoclose: true,
        todayHighlight: true,
        toggleActive: true
    };
    $('input.input-date').each(function () {
        $(this).datepicker(Object.assign({}, defaultConfig, {
            defaultViewDate: $(this).attr('startDate')
        }));
    });
})();
