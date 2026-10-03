package com.stgo.taostyle.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.stereotype.Component;

import com.stgo.taostyle.domain.Cart;
import com.stgo.taostyle.domain.City;
import com.stgo.taostyle.domain.Country;
import com.stgo.taostyle.domain.Customize;
import com.stgo.taostyle.domain.Person;
import com.stgo.taostyle.domain.Product;
import com.stgo.taostyle.domain.Service;
import com.stgo.taostyle.domain.TextContent;
import com.stgo.taostyle.domain.UserAccount;
import com.stgo.taostyle.service.CartService;
import com.stgo.taostyle.service.CityService;
import com.stgo.taostyle.service.CountryService;
import com.stgo.taostyle.service.CustomizeService;
import com.stgo.taostyle.service.PersonService;
import com.stgo.taostyle.service.ProductService;
import com.stgo.taostyle.service.ServiceService;
import com.stgo.taostyle.service.TextContentService;
import com.stgo.taostyle.service.UserAccountService;

/**
 * A central place to register application converters and formatters.
 */
@Component
public class ApplicationConversionServiceFactoryBean extends FormattingConversionServiceFactoryBean {
    @Autowired
    PersonService personService;
    @Autowired
    private CustomizeService customizeService;
    @Autowired
    private CityService cityService;
    @Autowired
    private CartService cartService;
    @Autowired
    private CountryService countryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ServiceService serviceService;
    @Autowired
    private TextContentService textContentService;
    @Autowired
    private UserAccountService userAccountService;
    public Converter<Country, String> getCountryToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.Country, java.lang.String>() {
            public String convert(
                    Country country) {
                if (country.getDescription() != null && !country.getDescription().isEmpty()) {
                    return new StringBuilder().append(country.getName()).append(" - ").append(country.getDescription())
                            .toString();
                } else {
                    return new StringBuilder().append(country.getName()).toString();
                }
            }
        };
    }
    
    public static void main(String[] args) {

    	byte[] GS_ExclamationMark = new byte[] {0x1d, 0x21, 0x11 };
    	
    	String c = "29, 33, 17";
    	String[] pieces = c.split(",");
    	if(pieces.length != 3) {
    		return;
    	}
    	
    	for(int i = 0; i < 3; i++) {
	    	GS_ExclamationMark[i] = Integer.valueOf(pieces[i].trim()).byteValue();
    	}
    	
    	String a = new String(GS_ExclamationMark);
    	byte[] b = a.getBytes();
    	if(b.length != 3 || b[0] != 0x1d || b[1] != 0x21 || b[2] != 0x11) {
    		System.out.println("fail");
    	}
    }

	public Converter<Cart, String> getCartToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.Cart, java.lang.String>() {
            public String convert(Cart cart) {
                return new StringBuilder().append(cart.getQuantity()).append(' ').append(cart.getWeight()).append(' ').append(cart.getUnit()).append(' ').append(cart.getCreatedDate()).toString();
            }
        };
    }

	public Converter<Long, Cart> getIdToCartConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.Cart>() {
            public com.stgo.taostyle.domain.Cart convert(java.lang.Long id) {
                return cartService.findCart(id);
            }
        };
    }

	public Converter<String, Cart> getStringToCartConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.Cart>() {
            public com.stgo.taostyle.domain.Cart convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), Cart.class);
            }
        };
    }

	public Converter<City, String> getCityToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.City, java.lang.String>() {
            public String convert(City city) {
                return new StringBuilder().append(city.getName()).toString();
            }
        };
    }

	public Converter<Long, City> getIdToCityConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.City>() {
            public com.stgo.taostyle.domain.City convert(java.lang.Long id) {
                return cityService.findCity(id);
            }
        };
    }

	public Converter<String, City> getStringToCityConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.City>() {
            public com.stgo.taostyle.domain.City convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), City.class);
            }
        };
    }

	public Converter<Long, Country> getIdToCountryConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.Country>() {
            public com.stgo.taostyle.domain.Country convert(java.lang.Long id) {
                return countryService.findCountry(id);
            }
        };
    }

	public Converter<String, Country> getStringToCountryConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.Country>() {
            public com.stgo.taostyle.domain.Country convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), Country.class);
            }
        };
    }

	public Converter<Customize, String> getCustomizeToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.Customize, java.lang.String>() {
            public String convert(Customize customize) {
                return new StringBuilder().append(customize.getCusKey()).append(' ').append(customize.getCusValue()).toString();
            }
        };
    }

	public Converter<Long, Customize> getIdToCustomizeConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.Customize>() {
            public com.stgo.taostyle.domain.Customize convert(java.lang.Long id) {
                return customizeService.findCustomize(id);
            }
        };
    }

	public Converter<String, Customize> getStringToCustomizeConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.Customize>() {
            public com.stgo.taostyle.domain.Customize convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), Customize.class);
            }
        };
    }

	public Converter<Person, String> getPersonToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.Person, java.lang.String>() {
            public String convert(Person person) {
                return new StringBuilder().append(person.getName()).toString();
            }
        };
    }

	public Converter<Long, Person> getIdToPersonConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.Person>() {
            public com.stgo.taostyle.domain.Person convert(java.lang.Long id) {
                return personService.findPerson(id);
            }
        };
    }

	public Converter<String, Person> getStringToPersonConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.Person>() {
            public com.stgo.taostyle.domain.Person convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), Person.class);
            }
        };
    }

	public Converter<Product, String> getProductToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.Product, java.lang.String>() {
            public String convert(Product product) {
                return new StringBuilder().append(product.getPartNo()).append(' ').append(product.getName()).append(' ').append(product.getProducedplace()).append(' ').append(product.getC1()).toString();
            }
        };
    }

	public Converter<Long, Product> getIdToProductConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.Product>() {
            public com.stgo.taostyle.domain.Product convert(java.lang.Long id) {
                return productService.findProduct(id);
            }
        };
    }

	public Converter<String, Product> getStringToProductConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.Product>() {
            public com.stgo.taostyle.domain.Product convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), Product.class);
            }
        };
    }

	public Converter<Service, String> getServiceToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.Service, java.lang.String>() {
            public String convert(Service service) {
                return new StringBuilder().append(service.getName()).append(' ').append(service.getDescription()).append(' ').append(service.getC1()).append(' ').append(service.getC2()).toString();
            }
        };
    }

	public Converter<Long, Service> getIdToServiceConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.Service>() {
            public com.stgo.taostyle.domain.Service convert(java.lang.Long id) {
                return serviceService.findService(id);
            }
        };
    }

	public Converter<String, Service> getStringToServiceConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.Service>() {
            public com.stgo.taostyle.domain.Service convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), Service.class);
            }
        };
    }

	public Converter<TextContent, String> getTextContentToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.TextContent, java.lang.String>() {
            public String convert(TextContent textContent) {
                return new StringBuilder().append(textContent.getPosInPage()).append(' ').append(textContent.getContent()).toString();
            }
        };
    }

	public Converter<Long, TextContent> getIdToTextContentConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.TextContent>() {
            public com.stgo.taostyle.domain.TextContent convert(java.lang.Long id) {
                return textContentService.findTextContent(id);
            }
        };
    }

	public Converter<String, TextContent> getStringToTextContentConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.TextContent>() {
            public com.stgo.taostyle.domain.TextContent convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), TextContent.class);
            }
        };
    }

	public Converter<UserAccount, String> getUserAccountToStringConverter() {
        return new org.springframework.core.convert.converter.Converter<com.stgo.taostyle.domain.UserAccount, java.lang.String>() {
            public String convert(UserAccount userAccount) {
                return new StringBuilder().append(userAccount.getLoginname()).append(' ').append(userAccount.getPassword()).append(' ').append(userAccount.getCel()).append(' ').append(userAccount.getTel()).toString();
            }
        };
    }

	public Converter<Long, UserAccount> getIdToUserAccountConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.Long, com.stgo.taostyle.domain.UserAccount>() {
            public com.stgo.taostyle.domain.UserAccount convert(java.lang.Long id) {
                return userAccountService.findUserAccount(id);
            }
        };
    }

	public Converter<String, UserAccount> getStringToUserAccountConverter() {
        return new org.springframework.core.convert.converter.Converter<java.lang.String, com.stgo.taostyle.domain.UserAccount>() {
            public com.stgo.taostyle.domain.UserAccount convert(String id) {
                return getObject().convert(getObject().convert(id, Long.class), UserAccount.class);
            }
        };
    }

	public void installLabelConverters(FormatterRegistry registry) {
        registry.addConverter(getCartToStringConverter());
        registry.addConverter(getIdToCartConverter());
        registry.addConverter(getStringToCartConverter());
        registry.addConverter(getCityToStringConverter());
        registry.addConverter(getIdToCityConverter());
        registry.addConverter(getStringToCityConverter());
        registry.addConverter(getCountryToStringConverter());
        registry.addConverter(getIdToCountryConverter());
        registry.addConverter(getStringToCountryConverter());
        registry.addConverter(getCustomizeToStringConverter());
        registry.addConverter(getIdToCustomizeConverter());
        registry.addConverter(getStringToCustomizeConverter());
        registry.addConverter(getPersonToStringConverter());
        registry.addConverter(getIdToPersonConverter());
        registry.addConverter(getStringToPersonConverter());
        registry.addConverter(getProductToStringConverter());
        registry.addConverter(getIdToProductConverter());
        registry.addConverter(getStringToProductConverter());
        registry.addConverter(getServiceToStringConverter());
        registry.addConverter(getIdToServiceConverter());
        registry.addConverter(getStringToServiceConverter());
        registry.addConverter(getTextContentToStringConverter());
        registry.addConverter(getIdToTextContentConverter());
        registry.addConverter(getStringToTextContentConverter());
        registry.addConverter(getUserAccountToStringConverter());
        registry.addConverter(getIdToUserAccountConverter());
        registry.addConverter(getStringToUserAccountConverter());
    }

	public void afterPropertiesSet() {
        super.afterPropertiesSet();
        installLabelConverters(getObject());
    }
}
