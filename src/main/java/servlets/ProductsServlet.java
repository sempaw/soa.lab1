package servlets;

import DAO.DAOImpl;
import entities.ProductsEntity;
import exceptions.OperationException;
import exceptions.WrongArgumentException;
import models.Person;
import models.Product;
import models.ProductsList;
import util.Converter;
import util.ExceptionsUtil;
import util.Validator;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProductsServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // добавление нового
        Writer out = response.getWriter();
        BufferedReader reader = request.getReader();
        Product product;
        try {
            product = Converter.xmlReaderToModel(reader, Product.class);
            product.setCreationDate(LocalDateTime.now());
            Validator.validateProduct(product, false);
            ProductsEntity productsEntity = Converter.modelToEntity(product);
            DAOImpl dao = new DAOImpl();
            dao.addProduct(productsEntity);
            response.setStatus(204);
        }
        catch ( WrongArgumentException e) {
            response.setStatus(400);
            out.write(e.getMessage());
        }
        catch (JAXBException e) {
            response.setStatus(400);
            out.write(ExceptionsUtil.getInvalidDataException());
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        DAOImpl dao = new DAOImpl();
        response.setCharacterEncoding("UTF-8");
        Writer out = response.getWriter();
        // получение по ид: GET /products/id/{id}
        if (request.getPathInfo() != null &&
            request.getPathInfo().startsWith("/id/") &&
            request.getPathInfo().length() - request.getPathInfo().replace("/", "").length() == 2) {
            try {
                String idPart = request.getPathInfo().substring(4);
                long id = Long.parseLong(idPart);
                ProductsEntity entity = dao.getProductById(id);
                Product model = Converter.entityToModel(entity);
                response.setContentType("application/xml");
                Converter.modelToXmlWriter(model, out, Product.class);
                response.setStatus(200);
            }
            catch (NumberFormatException numberFormatException) {
                response.setStatus(400);
                out.write(ExceptionsUtil.getWrongTypeException("Id", "int"));
            }
            catch (OperationException e) {
                response.setStatus(400);
                out.write(e.getMessage());
            }
            catch (JAXBException e) {
                response.setStatus(500);
                out.write(e.getMessage());
            }
        }
        // расчет средней цены производителя : GET /products/manufacture-cost/average
        else if (request.getPathInfo() != null &&
                 request.getPathInfo().equals("/manufacture-cost/average")) {
            response.setStatus(200);
            out.write(String.valueOf(dao.getAverageManufactureCost()));
        }
        // получение всех: GET /products
        else {
            try {
                String[] sortBy = request.getParameterValues("sort") == null
                        ? new String[]{}
                        : request.getParameterValues("sort");
                Integer pageNumber = request.getParameter("page-number") == null
                        ? null
                        : Integer.valueOf(request.getParameter("page-number"));
                Integer pageCapacity = request.getParameter("page-capacity") == null
                        ? null
                        : Integer.valueOf(request.getParameter("page-capacity"));
                if (pageNumber != null && pageNumber < 1) throw new WrongArgumentException(ExceptionsUtil.getShouldBeGreaterException("page-number", "0"));
                List<ProductsEntity> entities = dao.getProducts(request.getPathInfo(), pageNumber, pageCapacity, sortBy);
                ProductsList productsList = new ProductsList();
                List<Product> list = new ArrayList<>();
                for (ProductsEntity entity: entities) {
                    list.add(Converter.entityToModel(entity));
                }
                productsList.setProducts(list);
                response.setContentType("application/xml");
                Converter.modelToXmlWriter(productsList, out, ProductsList.class);
                response.setStatus(200);
            }
            catch (WrongArgumentException | OperationException e) {
                response.setStatus(400);
                out.write(e.getMessage());
            }
            catch (JAXBException e) {
                response.setStatus(500);
                out.write(e.getMessage());
            }
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Writer out = response.getWriter();
        DAOImpl dao = new DAOImpl();
        BufferedReader reader = request.getReader();
        // удаление: DELETE /products/id/{id}
        if (request.getPathInfo() != null && request.getPathInfo().startsWith("/id/")) {
            String idPart = request.getPathInfo().substring(4);
            try {
                long id = Long.parseLong(idPart);
                dao.deleteProductById(id);
                response.setStatus(204);
            }
            catch (NumberFormatException numberFormatException) {
                response.setStatus(400);
                out.write(ExceptionsUtil.getWrongTypeException("Id", "int"));
            }
            catch (OperationException operationException) {
                response.setStatus(404);
                out.write(operationException.getMessage());
            }
        }
        // удаление всех где есть овнер: DELETE /products/owner (в боди объект)
        else if (request.getPathInfo() != null && request.getPathInfo().equals("/owner")) {
            Person person;
            try {
                person = Converter.xmlReaderToModel(reader, Person.class);
                Validator.validatePerson(person);
                dao.deleteAllProductWithPerson(person);
                response.setStatus(204);
            }
            catch (WrongArgumentException | OperationException wa) {
                response.setStatus(400);
                out.write(wa.getMessage());
            }
            catch (JAXBException | NullPointerException e) {
                response.setStatus(400);
                out.write(ExceptionsUtil.getInvalidDataException());
            }
        }
        // удалить любой где есть прайс: DELETE /products/price/{price}
        else if (request.getPathInfo() != null && request.getPathInfo().startsWith("/price/")) {
            String pricePart = request.getPathInfo().substring(7);
            try {
                Integer price = pricePart.equals("")
                        ? null
                        : Integer.valueOf(pricePart);
                dao.deleteProductWithPrice(price);
                response.setStatus(204);
            }
            catch (NumberFormatException numberFormatException) {
                response.setStatus(400);
                out.write(ExceptionsUtil.getWrongTypeException("Price", "Integer"));
            }
            catch (OperationException operationException) {
                response.setStatus(404);
                out.write(operationException.getMessage());
            }
        }
        else {
            response.setStatus(404);
            out.write(ExceptionsUtil.getPageNotFoundException());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // обновление
        Product product;
        BufferedReader reader = request.getReader();
        Writer out = response.getWriter();
        try {
            product = Converter.xmlReaderToModel(reader, Product.class);
            Validator.validateProduct(product, true);
            DAOImpl dao = new DAOImpl();
            dao.getProductById(product.getId());
            ProductsEntity productsEntity = Converter.modelToEntity(product);
            dao.updateProduct(productsEntity);
            response.setStatus(204);

        }
        catch (WrongArgumentException | OperationException e) {
            response.setStatus(400);
            out.write(e.getMessage());
        }
        catch (JAXBException e) {
            response.setStatus(400);
            out.write(ExceptionsUtil.getInvalidDataException());
        }
    }
}
