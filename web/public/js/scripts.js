function slugify(str) {
    return (str || '')
        .trim()
        .toLowerCase()
        .replace(/[ _'"]/g, '-')
        .normalize('NFD').replace(/[^a-z0-9-]/g, '');
}

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
