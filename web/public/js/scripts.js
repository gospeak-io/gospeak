function slugify(str) {
    return (str || '')
        .trim()
        .toLowerCase()
        .replace(/[ _+'"]/g, '-')
        .replace(/--/g, '-')
        .normalize('NFD').replace(/[^a-z0-9-]/g, '');
}

// enable global features
(function () {
    $('[data-toggle="tooltip"]').tooltip();
    autosize($('textarea'));
})();

// confirm actions
(function () {
    $('[confirm]').click(function (e) {
        var text = $(this).attr('confirm') || $(this).attr('title') || 'Confirm?';
        if (!confirm(text)) {
            e.preventDefault();
        }
    });
})();

// autofocus when a modal opens
(function () {
    $('.modal').on('shown.bs.modal', function () {
        var $modal = $(this);
        $modal.find('input[autofocus], textarea[autofocus]').focus();
        autosize.update($modal.find('textarea'));
    });
})();

// disable inputs not in visible pane
(function () {
    // disable input inside hidden tabs
    $('.tab-pane.radio:not(.show)').find('input, textarea, select').attr('disabled', true);

    // enable input inside shown tab
    $('a.radio[data-toggle="tab"], a.radio[data-toggle="pill"]').on('shown.bs.tab', function (e) {
        $(e.target.getAttribute('href')).find('input, textarea, select').attr('disabled', false);
        if (e.relatedTarget) {
            $(e.relatedTarget.getAttribute('href')).find('input, textarea, select').attr('disabled', true);
        }
    });
})();

// build slug from an other field
(function () {
    function buildSlug(inputs, prev /* ?string */) {
        return inputs.map(function (input) {
            return prev && input.attr('id') === prev ? input.data('prev') : input.val();
        }).filter(function (value) {
            return !!value;
        }).map(function (value) {
            return slugify(value);
        }).join('-');
    }

    $('input[slug-for]').each(function () {
        var slugInput = $(this);
        var srcInputs = slugInput.attr('slug-for').split(',').map(function (id) {
            return $('#' + id);
        });
        srcInputs.forEach(function (srcInput) {
            srcInput.change(function () {
                var newSlug = buildSlug(srcInputs);
                var oldSlug = buildSlug(srcInputs, srcInput.attr('id'));
                srcInput.data('prev', srcInput.val());
                var curSlug = slugInput.val();
                if (curSlug === '' || curSlug === oldSlug) {
                    slugInput.val(newSlug);
                }
            });
        });
    });
})();

// remote input validation
(function () {
    $('input[remote-validate]').each(function () {
        var $input = $(this);
        var url = $input.attr('remote-validate');
        if (url) {
            update($input, url); // run on page load
            $input.change(function () {
                update($input, url);
            });
        }
    });

    function update($input, url) {
        var value = $input.val();
        if (value) {
            $.getJSON(url.replace('%7B%7Binput%7D%7D', value), function (res) {
                if ($input.val() === value) {
                    removeFeedback($input);
                    if (res.valid) {
                        addValidFeedback($input, res);
                    } else {
                        addInvalidFeedback($input, res);
                    }
                }
            });
        } else {
            removeFeedback($input);
        }
    }

    function removeFeedback($input) {
        $input.removeClass('is-valid');
        $input.removeClass('is-invalid');
        var $next = $input.next();
        if ($next.hasClass('valid-feedback') || $next.hasClass('invalid-feedback')) {
            $next.remove();
        }
    }

    function addValidFeedback($input, res) {
        $input.addClass('is-valid');
        if (res.message) {
            $input.after('<span class="valid-feedback">' + res.message + '</span>');
        }
    }

    function addInvalidFeedback($input, res) {
        $input.addClass('is-invalid');
        if (res.message) {
            $input.after('<span class="invalid-feedback">' + res.message + '</span>');
        }
    }
})();

// https://select2.org/ & https://github.com/select2/select2-bootstrap-theme
(function () {
    var defaultOpts = {
        theme: 'bootstrap',
        allowClear: true
    };
    $('select.select2').each(function () {
        addRemoteOptions2($(this), function ($select) {
            $select.select2(Object.assign({}, defaultOpts, {
                placeholder: $select.attr('placeholder')
            }));
        });
    });
    $('select.tags').each(function () {
        addRemoteOptions2($(this), function ($select) {
            $select.select2(Object.assign({}, defaultOpts, {
                placeholder: $select.attr('placeholder'),
                tags: true
            }));
        });
    });

    function addRemoteOptions($select) {
        var remote = $select.attr('remote');
        if (remote) {
            $.getJSON(remote, function (res) {
                var values = ($select.attr('value') || '').split(',').filter(function (v) {
                    return v.length > 0;
                });
                res.map(function (item) {
                    if ($select.find('option[value="' + item.id + '"]').length === 0) { // do not add item if it already exists
                        $select.append(new Option(item.text, item.id, false, values.indexOf(item.id) > -1));
                    }
                });
                $select.trigger('change');
            });
        }
    }

    function addRemoteOptions2($select, callback) {
        var remote = $select.attr('remote');
        if (remote) {
            $.getJSON(remote, function (res) {
                var values = ($select.attr('value') || '').split(',').filter(function (v) {
                    return v.length > 0;
                });
                res.map(function (item) {
                    if ($select.find('option[value="' + item.id + '"]').length === 0) { // do not add item if it already exists
                        if (values.indexOf(item.id) > -1) {
                            $select.append('<option value="' + item.id + '" selected>' + item.text + '</option>');
                        } else {
                            $select.append('<option value="' + item.id + '">' + item.text + '</option>');
                        }
                    }
                });
                callback($select);
            });
        } else {
            callback($select);
        }
    }
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

// inputImageUrl
(function () {
    $('.input-imageurl').each(function () {
        var $elt = $(this);
        var $input = $elt.find('input[type="text"]');
        var $preview = $elt.find('.preview');
        update($input, $preview); // run on page load
        $input.change(function () {
            update($input, $preview);
        });
    });

    function update($input, $preview) {
        if ($input.val() === '') {
            $preview.hide();
        } else {
            $preview.attr('src', $input.val());
            $preview.show();
        }
    }
})();

// markdown input
(function () {
    function fetchHtml(md) {
        return fetch('/ui/utils/markdown-to-html', {method: 'POST', body: md}).then(function (res) {
            return res.text();
        });
    }

    $('.markdown-editor').each(function () {
        var previewTab = $(this).find('a[data-toggle="tab"].preview');
        var textarea = $(this).find('textarea');
        var previewPane = $(this).find('.tab-pane.preview');
        var loadingHtml = previewPane.html();
        previewTab.on('show.bs.tab', function () {
            var md = textarea.val();
            fetchHtml(md).then(function (html) {
                previewPane.html(html);
            });
        });
        previewTab.on('hidden.bs.tab', function () {
            previewPane.html(loadingHtml);
        });
    });
})();

// template input & data
(function () {
    $('.template-data').each(function () {
        var $elt = $(this);
        var ref = $elt.attr('data-ref');
        var target = $elt.attr('data-target');
        var $target = target ? $('#' + target) : undefined;
        updateData($elt, ref);
        if($target) {
            $target.change(function () {
                updateData($elt, $target.val());
            });
        }
    });
    $('.template-editor').each(function () {
        var $elt = $(this);
        var ref = $elt.attr('data-ref');
        var target = $elt.attr('data-target');
        var $target = target ? $('#' + target) : undefined;
        var previewTab = $elt.find('a[data-toggle="tab"].preview');
        var $input = $elt.find('textarea,input[type="text"]');
        var previewPane = $elt.find('.tab-pane.preview');
        var loadingHtml = previewPane.html();
        previewTab.on('show.bs.tab', function () {
            var r = $target ? $target.val() : ref;
            updateTemplate($input, r, previewPane);
        });
        previewTab.on('hidden.bs.tab', function () {
            previewPane.html(loadingHtml);
        });
        if($target) {
            $target.change(function () {
                if(previewPane.hasClass('active')) {
                    updateTemplate($input, $target.val(), previewPane);
                }
            });
        }
    });

    function updateData($elt, ref) {
        if (ref) {
            fetchData(ref).then(function (res) {
                $elt.find('.json-viewer').html(JSON.stringify(res.data, null, 2));
                $elt.show();
            });
        } else {
            $elt.hide();
        }
    }

    function updateTemplate($input, ref, previewPane) {
        var tmpl = $input.val();
        fetchTemplate(tmpl, ref).then(function (tmpl) {
            if (tmpl.error) {
                console.warn('Template error', tmpl.error);
            }
            previewPane.html(tmpl.result);
        });
    }

    function fetchData(ref) {
        return fetch('/ui/utils/template-data/' + ref).then(function (res) {
            return res.json();
        });
    }

    function fetchTemplate(tmpl, ref) {
        return fetch('/ui/utils/render-template', {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                template: tmpl,
                ref: ref
            })
        }).then(function (res) {
            return res.json();
        });
    }
})();

// embed input & display
(function () {
    function fetchEmbedCode(url) {
        return fetch('/ui/utils/embed?url=' + url).then(function (res) {
            return res.text();
        });
    }

    function inputFetchEmbedCode(input, target) {
        var url = input.val();
        if (!!url) {
            target.html('<div class="d-flex justify-content-center m-5"><div class="spinner-border" role="status"><span class="sr-only">Loading...</span></div></div>');
            fetchEmbedCode(url).then(function (html) {
                // verify that input value haven't changed since the start of the query
                if (input.val() === url) {
                    target.html(html);
                }
            }, function () {
                if (input.val() === url) {
                    target.html('');
                }
            });
        } else {
            target.html('');
        }
    }

    $('.embed-editor').each(function () {
        var input = $(this).find('input');
        var embed = $(this).find('.embed');
        inputFetchEmbedCode(input, embed);
        input.change(function () {
            inputFetchEmbedCode(input, embed);
        });
    });
    $('.embed-display').each(function () {
        var embed = $(this);
        var url = embed.attr('data-url');
        fetchEmbedCode(url).then(function (html) {
            embed.html(html);
        });
    });
})();

// GMapPlace picker (https://developers.google.com/maps/documentation/javascript/examples/places-autocomplete?hl=fr)
var GMapPlacePicker = (function () {
    function initMap($map) {
        var map = new google.maps.Map($map.get(0), {
            center: {lat: -33.8688, lng: 151.2195},
            zoom: 13
        });
        var marker = new google.maps.Marker({
            map: map,
            anchorPoint: new google.maps.Point(0, -29)
        });
        var infowindow = new google.maps.InfoWindow();
        return {
            $map: $map,
            map: map,
            marker: marker,
            infowindow: infowindow
        };
    }

    function toggleMap(mapData, location) {
        if (location && location.lat && location.lng) {
            showMap(mapData, location);
        } else {
            hideMap(mapData);
        }
    }

    function showMap(mapData, location) {
        var point = {lat: parseFloat(location.lat), lng: parseFloat(location.lng)};
        mapData.$map.show();
        google.maps.event.trigger(mapData.map, 'resize');
        mapData.infowindow.close();
        mapData.marker.setVisible(false);
        mapData.map.setCenter(point);
        mapData.map.setZoom(15);
        mapData.marker.setPosition(point);
        mapData.marker.setVisible(true);
        mapData.infowindow.setContent(
            '<strong>' + location.name + '</strong><br>' +
            location.streetNo + ' ' + location.street + '<br>' +
            location.postalCode + ' ' + location.locality + ', ' + location.country
        );
        mapData.infowindow.open(mapData.map, mapData.marker);
    }

    function hideMap(mapData) {
        mapData.$map.hide();
    }

    var fields = ['id', 'name', 'streetNo', 'street', 'postalCode', 'locality', 'country', 'formatted', 'lat', 'lng', 'url', 'website', 'phone', 'utcOffset'];

    function writeForm($elt, location) {
        fields.forEach(function (field) {
            $elt.find('input.gmapplace-' + field).val(location ? location[field] : '');
        });
    }

    function readForm($elt) {
        var location = {};
        fields.forEach(function (field) {
            location[field] = $elt.find('input.gmapplace-' + field).val();
        });
        return location;
    }

    function toLocation(place) {
        function getSafe(elt, field) {
            return elt && elt[field] ? elt[field] : '';
        }

        function toAddress(components) {
            function getByType(components, type) {
                var c = components.find(function (e) {
                    return e.types.indexOf(type) >= 0;
                });
                return c && c.long_name;
            }

            return {
                streetNumber: getByType(components, 'street_number'),
                route: getByType(components, 'route'),
                postalCode: getByType(components, 'postal_code'),
                locality: getByType(components, 'locality'),
                country: getByType(components, 'country'),
                administrativeArea: {
                    level1: getByType(components, 'administrative_area_level_1'),
                    level2: getByType(components, 'administrative_area_level_2'),
                    level3: getByType(components, 'administrative_area_level_3'),
                    level4: getByType(components, 'administrative_area_level_4'),
                    level5: getByType(components, 'administrative_area_level_5')
                },
                sublocality: {
                    level1: getByType(components, 'sublocality_level_1'),
                    level2: getByType(components, 'sublocality_level_2'),
                    level3: getByType(components, 'sublocality_level_3'),
                    level4: getByType(components, 'sublocality_level_4'),
                    level5: getByType(components, 'sublocality_level_5')
                }
            };
        }

        var address = toAddress(place.address_components);
        var loc = place && place.geometry && place.geometry.location;
        return {
            id: getSafe(place, 'place_id'),
            name: getSafe(place, 'name'),
            streetNo: getSafe(address, 'streetNumber'),
            street: getSafe(address, 'route'),
            postalCode: getSafe(address, 'postalCode'),
            locality: getSafe(address, 'locality'),
            country: getSafe(address, 'country'),
            formatted: getSafe(place, 'formatted_address'),
            lat: loc ? loc.lat().toString() : '',
            lng: loc ? loc.lng().toString() : '',
            url: getSafe(place, 'url'),
            website: getSafe(place, 'website'),
            phone: getSafe(place, 'international_phone_number'),
            utcOffset: getSafe(place, 'utc_offset').toString()
        };
    }

    function initAutocomplete($elt, $input, mapData) {
        var autocomplete = new google.maps.places.Autocomplete($input.get(0));
        autocomplete.addListener('place_changed', function () {
            var place = autocomplete.getPlace(); // cf https://developers.google.com/maps/documentation/javascript/reference/places-service?hl=fr#PlaceResult
            var location = toLocation(place);
            writeForm($elt, location);
            toggleMap(mapData, location);
        });
    }

    return {
        init: function () {
            $('.gmapplace-input').each(function () {
                var $elt = $(this);
                var $input = $elt.find('input[type="text"]');
                var $map = $elt.find('.map');
                var mapData = initMap($map);
                var location = readForm($elt);
                toggleMap(mapData, location);
                initAutocomplete($elt, $input, mapData);
                // prevent form submit on enter
                $input.on('keydown', function (e) {
                    if (e && e.keyCode === 13) {
                        e.preventDefault();
                    }
                });
                // clear form when input is cleared
                $input.on('change', function () {
                    if ($input.val() === '') {
                        var location = null;
                        writeForm($elt, location);
                        toggleMap(mapData, location);
                    }
                });
            });
        }
    };
})();

function googleMapsInit() {
    GMapPlacePicker.init();
}
